package com.maxwell.chronos.service;

import com.maxwell.chronos.dto.CompanyEmploymentDTO;
import com.maxwell.chronos.enums.AuditAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyEmploymentService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final AuditService audit;

    public record Input(@Size(max=50) String employeeId, @Size(max=120) String jobTitle,
                        java.time.LocalDate joiningDate, @NotNull @Min(0) Long version) {}

    private boolean canManage(long companyId, long actorId) {
        return Boolean.TRUE.equals(access.companyPermissions(companyId,actorId).capabilities().get("canManageCompanyPeople"));
    }

    public CompanyEmploymentDTO get(long companyId,long targetId,long actorId) {
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN")) throw new AccessDeniedException("Company membership permission required");
        boolean admin=canManage(companyId,actorId); // Also validates active account and company membership.
        if(actorId!=targetId && !admin) throw new AccessDeniedException("Company Admin permission required");
        var rows=db.query("SELECT company_id,user_id,employee_id,job_title,joining_date,status,employment_version " +
                "FROM company_memberships WHERE company_id=? AND user_id=?",(rs,i)->new CompanyEmploymentDTO(
                    rs.getLong("company_id"),rs.getLong("user_id"),rs.getString("employee_id"),rs.getString("job_title"),
                    rs.getObject("joining_date",java.time.LocalDate.class),rs.getString("status"),rs.getLong("employment_version")),companyId,targetId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company member not found");
        return rows.get(0);
    }

    public CompanyEmploymentDTO update(long companyId,long targetId,long actorId,Input input) {
        if(!canManage(companyId,actorId)) throw new AccessDeniedException("Company Admin permission required");
        var before=get(companyId,targetId,actorId);
        if(!"ACTIVE".equals(before.membershipStatus())) throw new AccessDeniedException("Only active company memberships can be edited");
        if(input.version()==null || input.version()<0) throw new IllegalArgumentException("Reload employment details before editing");
        String employeeId=clean(input.employeeId(),50), title=clean(input.jobTitle(),120);
        if(employeeId!=null&&!employeeId.matches("[1-9][0-9]{0,8}"))throw new IllegalArgumentException("Employee ID must be a number between 1 and 999999999");
        int changed;
        try {
            changed=db.update("UPDATE company_memberships SET employee_id=?,job_title=?,joining_date=?, " +
                "employment_version=employment_version+1,employment_updated_at=now() " +
                "WHERE company_id=? AND user_id=? AND status='ACTIVE' AND employment_version=?",
                employeeId,title,input.joiningDate(),companyId,targetId,input.version());
        } catch(org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"That employee ID is already in use in this company");
        }
        if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Employment details changed. Reload and try again.");
        long membershipId=db.queryForObject("SELECT id FROM company_memberships WHERE company_id=? AND user_id=?",Long.class,companyId,targetId);
        audit.logRequiredAction(actorId,AuditAction.USER_PROFILE_UPDATED,"CompanyMembership",membershipId,
                "Employment details updated for company " + companyId + ", member " + targetId);
        return get(companyId,targetId,actorId);
    }

    private String clean(String value,int maximum) {
        if(value==null) return null;
        value=value.trim();
        if(value.length()>maximum) throw new IllegalArgumentException("Employment field exceeds the allowed length");
        return value.isEmpty()?null:value;
    }
}
