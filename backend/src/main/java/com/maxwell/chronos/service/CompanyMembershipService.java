package com.maxwell.chronos.service;

import com.maxwell.chronos.enums.AuditAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyMembershipService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final AuditService audit;
    public record StatusInput(@NotBlank @Pattern(regexp="ACTIVE|REMOVED") String status,@NotNull @Min(0) Long version) {}
    public record RoleInput(@NotBlank @Pattern(regexp="COMPANY_ADMIN|PROJECT_ADMIN") String role,@NotNull @Min(0) Long version) {}
    public record Result(long userId,String status,long version) {}

    private void admin(long company,long actor){access.requireCompanyCapability(company,actor,"canManageCompanyPeople");}
    private Map<String,Object> target(long company,long user,Long version){
        if(version==null||version<0)throw new IllegalArgumentException("Reload the member before changing access");
        var rows=db.queryForList("SELECT m.id,m.status,m.membership_version,u.email, " +
            "(u.is_active AND u.password_hash IS NOT NULL AND NOT u.admin_locked) AS account_available, " +
            "EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.role_key='PLATFORM_ADMIN' " +
            "AND r.company_id IS NULL AND r.project_id IS NULL AND r.removed_at IS NULL) AS platform_account " +
            "FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=? AND m.user_id=? FOR UPDATE OF m",company,user);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company member not found");
        var row=rows.get(0);
        if(((Number)row.get("membership_version")).longValue()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Member access changed. Reload and try again.");
        return row;
    }
    private void requireActive(Map<String,Object> target){
        if(!"ACTIVE".equals(target.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"An active company membership is required");
    }
    private void requireAvailableAccount(Map<String,Object> target){
        if(!Boolean.TRUE.equals(target.get("account_available"))||Boolean.TRUE.equals(target.get("platform_account")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"The account is unavailable for company access. Resolve its platform account status first.");
    }
    private void revokeAccess(long company,long user){
        db.update("UPDATE company_sensitive_grants SET revoked_at=now(),version=version+1 WHERE company_id=? AND (user_id=? OR subject_user_id=?) AND revoked_at IS NULL",company,user,user);
        db.update("UPDATE role_assignments SET removed_at=now() WHERE company_id=? AND user_id=? AND removed_at IS NULL",company,user);
        db.update("UPDATE project_memberships SET status='REMOVED',removed_at=now() WHERE user_id=? AND status<>'REMOVED' " +
            "AND project_id IN(SELECT id FROM projects WHERE company_id=?)",user,company);
        db.update("UPDATE project_assignments SET is_active=FALSE WHERE user_id=? AND is_active=TRUE " +
            "AND project_id IN(SELECT id FROM projects WHERE company_id=?)",user,company);
        db.update("UPDATE moderator_grants SET revoked_at=now() WHERE company_id=? AND moderator_user_id=? AND revoked_at IS NULL",company,user);
    }
    private void record(long actor,AuditAction action,Map<String,Object> member,long company,long user){
        audit.logRequiredAction(actor,action,"CompanyMembership",((Number)member.get("id")).longValue(),
            "Company "+company+", member "+user);
    }
    public Result changeStatus(long company,long user,long actor,StatusInput input){
        admin(company,actor);if("ACTIVE".equals(input.status()))access.lockAccountAdministration(user);access.lockCompanyAdministration(company);admin(company,actor);
        var member=target(company,user,input.version());
        if("REMOVED".equals(input.status())){
            requireActive(member);access.requireAnotherCompanyAdmin(company,user);
            boolean duties=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM projects WHERE company_id=? " +
                "AND status NOT IN ('COMPLETED','ARCHIVED') AND (owner_user_id=? OR project_manager_id=? OR project_manager_hours_approver_id=?))",
                Boolean.class,company,user,user,user));
            if(duties)throw new ResponseStatusException(HttpStatus.CONFLICT,"Transfer project ownership and reassign manager/approver duties before removing this member");
            boolean pending=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM timesheet_approval_periods a JOIN projects p ON p.id=a.project_id " +
                "WHERE p.company_id=? AND a.user_id=? AND a.status='SUBMITTED') OR EXISTS(SELECT 1 FROM project_expenses e JOIN projects p ON p.id=e.project_id " +
                "WHERE p.company_id=? AND e.employee_id=? AND e.status='PENDING_APPROVAL')",Boolean.class,company,user,company,user));
            if(pending)throw new ResponseStatusException(HttpStatus.CONFLICT,"Review pending time and expense submissions before removing this member");
            if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM vacation_requests WHERE company_id=? AND user_id=? AND status='SUBMITTED')",Boolean.class,company,user)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Review pending leave requests before removing this member");
            if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM letter_requests WHERE company_id=? AND user_id=? AND status='SUBMITTED')",Boolean.class,company,user)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Review pending letter requests before removing this member");
            revokeAccess(company,user);
            db.update("UPDATE company_invitations SET revoked_at=now() WHERE company_id=? AND lower(invitee_email)=lower(?) " +
                "AND accepted_at IS NULL AND revoked_at IS NULL",company,member.get("email"));
            db.update("UPDATE company_memberships SET status='REMOVED',removed_at=now(),membership_version=membership_version+1 " +
                "WHERE company_id=? AND user_id=?",company,user);
            record(actor,AuditAction.COMPANY_MEMBERSHIP_REMOVED,member,company,user);
        }else if("ACTIVE".equals(input.status())){
            if(!"REMOVED".equals(member.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only removed memberships can be reactivated");
            requireAvailableAccount(member);revokeAccess(company,user); // Never revive legacy roles or grants.
            db.update("UPDATE company_memberships SET status='ACTIVE',removed_at=NULL,joined_at=COALESCE(joined_at,now()), " +
                "membership_version=membership_version+1 WHERE company_id=? AND user_id=?",company,user);
            record(actor,AuditAction.COMPANY_MEMBERSHIP_REACTIVATED,member,company,user);
        }else throw new IllegalArgumentException("Choose ACTIVE or REMOVED membership status");
        return new Result(user,input.status(),input.version()+1);
    }
    public Result assignRole(long company,long user,long actor,RoleInput input){
        if(input.role()==null||!Set.of("COMPANY_ADMIN","PROJECT_ADMIN").contains(input.role()))throw new IllegalArgumentException("Choose a company role");
        admin(company,actor);
        if("COMPANY_ADMIN".equals(input.role()))access.lockAccountAdministration(user);
        access.lockCompanyAdministration(company);admin(company,actor);
        var member=target(company,user,input.version());requireActive(member);requireAvailableAccount(member);
        int added=db.update("INSERT INTO role_assignments(user_id,role_key,company_id,assigned_by_user_id) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",user,input.role(),company,actor);
        if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Member already has this company role");
        db.update("UPDATE company_memberships SET membership_version=membership_version+1 WHERE company_id=? AND user_id=?",company,user);
        record(actor,AuditAction.COMPANY_ROLE_ASSIGNED,member,company,user);
        return new Result(user,"ACTIVE",input.version()+1);
    }
    public Result removeRole(long company,long user,long actor,String role,Long version){
        if(role==null||!Set.of("COMPANY_ADMIN","PROJECT_ADMIN").contains(role))throw new IllegalArgumentException("Choose a company role");
        admin(company,actor);access.lockCompanyAdministration(company);admin(company,actor);
        var member=target(company,user,version);requireActive(member);
        if("COMPANY_ADMIN".equals(role))access.requireAnotherCompanyAdmin(company,user);
        int changed=db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND company_id=? " +
                "AND project_id IS NULL AND role_key=? AND removed_at IS NULL",user,company,role);
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Active company role not found");
        if("MODERATOR".equals(role))db.update("UPDATE moderator_grants SET revoked_at=now() WHERE company_id=? AND moderator_user_id=? AND revoked_at IS NULL",company,user);
        db.update("UPDATE company_memberships SET membership_version=membership_version+1 WHERE company_id=? AND user_id=?",company,user);
        record(actor,AuditAction.ROLE_CHANGED,member,company,user);
        return new Result(user,"ACTIVE",version+1);
    }

    @Transactional(readOnly=true)
    public String recoveryAddress(long company,long user,long actor){
        admin(company,actor);
        var addresses=db.queryForList("SELECT u.email FROM company_memberships m JOIN users u ON u.id=m.user_id " +
            "WHERE m.company_id=? AND m.user_id=? AND m.status='ACTIVE'",String.class,company,user);
        if(addresses.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active company member not found");
        return addresses.get(0);
    }
}
