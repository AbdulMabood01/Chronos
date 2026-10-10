package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class CompanyWorkflowAccess {
    // One definition shared by configuration, notifications, and delivery checks.
    static final String REPORT_HANDLERS_SQL = """
        SELECT m.user_id FROM company_memberships m JOIN users u ON u.id=m.user_id
        WHERE m.company_id=? AND m.status='ACTIVE' AND u.is_active AND NOT u.admin_locked
        AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL)
        AND (EXISTS(SELECT 1 FROM role_assignments r WHERE r.company_id=m.company_id AND r.user_id=m.user_id AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL)
          OR EXISTS(SELECT 1 FROM company_sensitive_grants g WHERE g.company_id=m.company_id AND g.user_id=m.user_id
            AND g.permission='CONFIDENTIAL_HANDLER' AND g.revoked_at IS NULL
            AND g.starts_on<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND g.ends_on>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date))
        """;
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final UserRepository users;
    public enum Permission { PERFORMANCE_REVIEW, CONFIDENTIAL_HANDLER }
    public boolean canHandleReports(long company,long user) {
        return access.hasCompanyRole(company,user,"COMPANY_ADMIN") || permitted(company,user,"CONFIDENTIAL_HANDLER",null);
    }
    public record Grant(@NotNull Long userId,@NotNull Permission permission,Long subjectUserId,
        @NotNull LocalDate startsOn,@NotNull LocalDate endsOn,@NotBlank @Size(max=500) String purpose) {}
    public User member(long company,String email) {
        User u=users.findByEmail(email).orElseThrow(()->new AccessDeniedException("Access denied"));
        access.requireActiveCompanyAccess(company,u.getId());
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active AND NOT admin_locked AND password_hash IS NOT NULL)",Boolean.class,u.getId())))throw new AccessDeniedException("An available company account is required");
        return u;
    }
    public User admin(long company,String email) {
        User u=member(company,email);access.requireCompanyCapability(company,u.getId(),"canManageCompanyPeople");return u;
    }
    public User lockMember(long company,String email,boolean admin) {
        User u=admin?admin(company,email):member(company,email);
        access.lockCompanyAdministration(company);return admin?admin(company,email):member(company,email);
    }
    public void target(long company,long user) {
        access.requireActiveCompanyAccess(company,user);
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active AND NOT admin_locked AND password_hash IS NOT NULL)",Boolean.class,user)))
            throw new IllegalArgumentException("Select an available company member");
    }
    public boolean permitted(long company,long user,String permission,Long subject) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_sensitive_grants WHERE company_id=? AND user_id=? AND permission=? AND revoked_at IS NULL AND starts_on<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND ends_on>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND (?::bigint IS NULL OR subject_user_id=?))",Boolean.class,company,user,permission,subject,subject));
    }
    public void reviewer(long company,long user,long subject) {
        access.requireActiveCompanyAccess(company,user);
        if(user==subject || !permitted(company,user,"PERFORMANCE_REVIEW",subject))throw new AccessDeniedException("An explicit permission for this review subject is required");
    }
    public void activity(long company,long actor,String action,String type,Object id) {
        db.update("INSERT INTO company_activity(company_id,user_id,action,entity_type,entity_id) VALUES (?,?,?,?,?)",company,actor,action,type,id==null?null:id.toString());
    }
    public List<Map<String,Object>> grants(long company,String email) {
        admin(company,email);
        return db.queryForList("SELECT g.*,concat_ws(' ',u.first_name,u.last_name) user_name,concat_ws(' ',s.first_name,s.last_name) subject_name FROM company_sensitive_grants g JOIN users u ON u.id=g.user_id LEFT JOIN users s ON s.id=g.subject_user_id WHERE g.company_id=? ORDER BY g.granted_at DESC,g.id",company);
    }
    public UUID grant(long company,String email,Grant input) {
        User actor=admin(company,email);access.lockAccountAdministration(input.userId());access.lockCompanyAdministration(company);actor=admin(company,email);target(company,input.userId());
        if(actor.getId().equals(input.userId()))throw new AccessDeniedException("Another Company Admin must authorize your sensitive access grant");
        if(input.startsOn().isAfter(input.endsOn()) || input.endsOn().isAfter(input.startsOn().plusYears(1)))throw new IllegalArgumentException("Grant dates must cover no more than one year");
        if(input.permission()==Permission.PERFORMANCE_REVIEW){
            if(input.subjectUserId()==null || input.subjectUserId().equals(input.userId()))throw new IllegalArgumentException("Select a different review subject");
            target(company,input.subjectUserId());
        }else if(input.subjectUserId()!=null)throw new IllegalArgumentException("Handler grants apply to eligible company cases");
        UUID id=UUID.randomUUID();db.update("INSERT INTO company_sensitive_grants(id,company_id,user_id,permission,subject_user_id,starts_on,ends_on,purpose,granted_by) VALUES (?,?,?,?,?,?,?,?,?)",id,company,input.userId(),input.permission().name(),input.subjectUserId(),input.startsOn(),input.endsOn(),input.purpose().trim(),actor.getId());
        activity(company,actor.getId(),"SENSITIVE_ACCESS_GRANTED","SensitiveGrant",id);return id;
    }
    public void revoke(long company,String email,UUID id,long version) {
        User actor=lockMember(company,email,true);
        int changed=db.update("UPDATE company_sensitive_grants SET revoked_at=now(),revoked_by=?,version=version+1 WHERE id=? AND company_id=? AND revoked_at IS NULL AND version=?",actor.getId(),id,company,version);
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Grant changed; reload before revoking");
        activity(company,actor.getId(),"SENSITIVE_ACCESS_REVOKED","SensitiveGrant",id);
    }
    public Map<String,Object> configuration(long company,String email) {
        member(company,email);
        return Map.of("companyId",company,"handlerConfigured",Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(" + REPORT_HANDLERS_SQL + ")",Boolean.class,company)));
    }
    public List<Map<String,Object>> audit(long company,String email,int page) {
        User actor=member(company,email);boolean admin=access.hasCompanyRole(company,actor.getId(),"COMPANY_ADMIN");
        if(page<0 || page>100000)throw new IllegalArgumentException("Invalid page");
        // Never return legacy details, grant purposes, feedback, review snapshots or case identifiers/content.
        return db.queryForList("SELECT a.id,a.action,a.\"entityType\",a.\"entityId\",a.\"createdAt\",concat_ws(' ',u.first_name,u.last_name) AS \"userName\" FROM (SELECT 'legacy-'||id id,user_id,action::text action,entity_type AS \"entityType\",entity_id::text AS \"entityId\",created_at AS \"createdAt\",project_id FROM audit_logs WHERE company_id=? AND entity_type IN ('Company','CompanyMembership','Project','Timesheet','TimesheetProjectSubmission','TimesheetApprovalPeriod','ProjectExpense','VacationRequest','LetterRequest') UNION ALL SELECT 'company-'||id,user_id,action,entity_type,entity_id,created_at,NULL::bigint FROM company_activity WHERE company_id=?) a LEFT JOIN users u ON u.id=a.user_id WHERE (? OR a.user_id=? OR EXISTS(SELECT 1 FROM role_assignments r JOIN project_memberships m ON m.project_id=r.project_id AND m.user_id=r.user_id WHERE r.company_id=? AND r.project_id=a.project_id AND r.user_id=? AND r.role_key='PROJECT_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE')) ORDER BY a.\"createdAt\" DESC,a.id LIMIT 100 OFFSET ?",company,company,admin,actor.getId(),company,actor.getId(),page*100);
    }
}
