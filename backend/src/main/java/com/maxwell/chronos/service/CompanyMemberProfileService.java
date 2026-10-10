package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.AuditAction;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional @RequiredArgsConstructor
public class CompanyMemberProfileService {
    private final CompanyAccessService access;
    private final CompanyEmploymentService employment;
    private final UserRepository users;
    private final UserService profiles;
    private final JdbcTemplate db;
    private final AuditService audit;
    private User authorized(long company,long target,long actor) {
        if (access.hasPlatformRole(actor,"PLATFORM_ADMIN") || !Boolean.TRUE.equals(access.companyPermissions(company,actor).capabilities().get("canManageCompanyPeople")))
            throw new AccessDeniedException("Company Admin permission required");
        access.lockAccountAdministration(target);
        access.lockCompanyAdministration(company);
        if (!Boolean.TRUE.equals(access.companyPermissions(company,actor).capabilities().get("canManageCompanyPeople")) || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_memberships WHERE company_id=? AND user_id=? AND status='ACTIVE')",Boolean.class,company,target)))
            throw new AccessDeniedException("Active company member required");
        if (access.hasPlatformRole(target,"PLATFORM_ADMIN")) throw new AccessDeniedException("Company member profile required");
        return users.findById(target).orElseThrow(()->new AccessDeniedException("Company member profile required"));
    }
    public UserDTO get(long company,long target,long actor) {
        User user=authorized(company,target,actor);
        var result=new UserDTO();
        BeanUtils.copyProperties(user,result,"ssnLast4","bloodGroup","employeeId","jobTitle","role","adminLocked","lockedUntil");
        var job=employment.get(company,target,actor);
        result.setEmployeeId(job.employeeId());result.setJobTitle(job.jobTitle());result.setAccountStatus(user.getAccountStatus());
        audit.logRequiredAction(actor,AuditAction.USER_PROFILE_UPDATED,"Company",company,"Company member profile viewed: " + target);
        return result;
    }
    public UserDTO update(long company,long target,long actor,UpdateProfileRequest input) {
        throw new AccessDeniedException("Personal profiles are view-only for company administrators. Employees update their own details.");
    }
}
