package com.maxwell.chronos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.maxwell.chronos.dto.ScopedPermissions;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves current permissions from the database, so removed roles stop working immediately. */
@Service
@RequiredArgsConstructor
public class CompanyAccessService {
    private final JdbcTemplate db;

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, args));
    }

    public void lockCompanyAdministration(Long companyId) {
        if(db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,companyId).isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Company not found");
    }

    public void lockAccountAdministration(long userId) {
        if(db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,userId).isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Account not found");
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?::text,0))",Object.class,"chronos-admin-account:"+userId);
    }
    public void requireCompanyAvailable(long company){
        if(!exists("SELECT EXISTS(SELECT 1 FROM companies WHERE id=? AND NOT is_suspended)",company))throw new AccessDeniedException("This company is suspended. Contact your administrator.");
    }
    public void requireAvailableAccount(long user){
        if(!exists("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active AND NOT admin_locked AND password_hash IS NOT NULL)",user))throw new AccessDeniedException("An available account is required");
    }

    private static final String EFFECTIVE_ADMIN = " FROM role_assignments r JOIN company_memberships m " +
        "ON m.company_id=r.company_id AND m.user_id=r.user_id JOIN users u ON u.id=r.user_id " +
        "WHERE r.company_id=? AND r.role_key='COMPANY_ADMIN' AND r.project_id IS NULL AND r.removed_at IS NULL " +
        "AND m.status='ACTIVE' AND u.is_active=TRUE AND u.password_hash IS NOT NULL AND u.admin_locked=FALSE " +
        "AND NOT EXISTS(SELECT 1 FROM role_assignments platform WHERE platform.user_id=u.id AND platform.role_key='PLATFORM_ADMIN' " +
        "AND platform.company_id IS NULL AND platform.project_id IS NULL AND platform.removed_at IS NULL)";

    public void requireAnotherCompanyAdmin(long companyId,long targetId) {
        if(!exists("SELECT EXISTS(SELECT 1"+EFFECTIVE_ADMIN+" AND u.id=?)",companyId,targetId))return;
        Integer others=db.queryForObject("SELECT count(DISTINCT u.id)"+EFFECTIVE_ADMIN+" AND u.id<>?",Integer.class,companyId,targetId);
        if(others==null || others==0)throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"Appoint another active Company Admin before removing this administrator's access");
    }

    public void guardAccountAdminAccessLoss(long targetId) {
        // Match the lock order used when granting company-admin roles and accepting invitations.
        lockAccountAdministration(targetId);
        var companies=db.queryForList("SELECT DISTINCT r.company_id FROM role_assignments r JOIN company_memberships m " +
                "ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.user_id=? AND r.role_key='COMPANY_ADMIN' " +
                "AND r.project_id IS NULL AND r.removed_at IS NULL AND m.status='ACTIVE' ORDER BY r.company_id",Long.class,targetId);
        for(long company:companies){lockCompanyAdministration(company);requireAnotherCompanyAdmin(company,targetId);}
    }

    // Internal read helpers for already-authorized company/project DTOs and exports.
    // Never fall back to another company or the legacy global employment snapshot.
    public String employmentEmployeeId(Long companyId,Long userId) { return employmentField(companyId,userId,"employee_id"); }
    public String employmentJobTitle(Long companyId,Long userId) { return employmentField(companyId,userId,"job_title"); }
    public String companyDisplayName(Long companyId) {
        if(companyId==null)return null;
        var names=db.queryForList("SELECT company_name FROM company_settings WHERE company_id=?",String.class,companyId);
        return names.isEmpty()?null:names.get(0);
    }
    private String employmentField(Long companyId,Long userId,String field) {
        if(companyId==null || userId==null) return null;
        var values=db.queryForList("SELECT " + field + " FROM company_memberships WHERE company_id=? AND user_id=?",String.class,companyId,userId);
        return values.isEmpty()?null:values.get(0);
    }

    private boolean activeAccount(long userId) {
        return exists("SELECT EXISTS (SELECT 1 FROM users WHERE id=? AND is_active=TRUE)", userId);
    }

    public boolean hasActiveCompanyAccess(Long companyId,long userId) {
        return companyId!=null && activeAccount(userId) && !hasPlatformRole(userId,"PLATFORM_ADMIN") &&
            exists("SELECT EXISTS(SELECT 1 FROM company_memberships m JOIN companies c ON c.id=m.company_id WHERE m.company_id=? AND m.user_id=? AND m.status='ACTIVE' AND NOT c.is_suspended)",companyId,userId);
    }
    public void requireActiveCompanyAccess(Long companyId,long userId) {
        if(!hasActiveCompanyAccess(companyId,userId))throw new AccessDeniedException("Active company membership is required to access company work records");
    }

    public ScopedPermissions.Platform platformPermissions(long userId) {
        boolean platform = activeAccount(userId) && hasPlatformRole(userId, "PLATFORM_ADMIN");
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        for (String key : List.of("canCreateCompanies", "canSuspendCompanies", "canManageCompanyPlans",
                "canManagePlatformAdmins", "canConfigurePlatform", "canViewPlatformAudit")) capabilities.put(key, platform);
        // Explicit support grants do not exist yet; never infer them from the platform role.
        capabilities.put("canAccessCompanySupport", false);
        return new ScopedPermissions.Platform(platform ? List.of("PLATFORM_ADMIN") : List.of(), Map.copyOf(capabilities));
    }

    public ScopedPermissions.Company companyPermissions(long companyId, long userId) {
        if (!activeAccount(userId)) throw new AccessDeniedException("An active account is required");
        // Platform context permits metadata administration only, even if an operational role was also assigned.
        if (hasPlatformRole(userId, "PLATFORM_ADMIN"))
            return new ScopedPermissions.Company(companyId, List.of(), companyCapabilities(false, false, List.of()), List.of());
        requireCompanyAvailable(companyId);
        if (!exists("SELECT EXISTS (SELECT 1 FROM company_memberships WHERE company_id=? AND user_id=? AND status='ACTIVE')",
                companyId, userId)) throw new AccessDeniedException("Active company membership is required");

        var roles = db.queryForList("SELECT DISTINCT r.role_key FROM role_assignments r " +
                "WHERE r.company_id=? AND r.user_id=? AND r.project_id IS NULL AND r.removed_at IS NULL " +
                "AND r.role_key IN ('COMPANY_ADMIN','PROJECT_ADMIN') ORDER BY r.role_key",
                String.class, companyId, userId);
        boolean companyAdmin = roles.contains("COMPANY_ADMIN");
        boolean creator = roles.contains("PROJECT_ADMIN");


        var assigned = db.queryForList("SELECT r.project_id, r.role_key FROM role_assignments r " +
                "JOIN projects p ON p.id=r.project_id AND p.company_id=r.company_id " +
                "JOIN project_memberships pm ON pm.project_id=p.id AND pm.user_id=r.user_id AND pm.status='ACTIVE' " +
                "WHERE r.company_id=? AND r.user_id=? AND r.removed_at IS NULL " +
                "AND r.role_key IN ('PROJECT_ADMIN','PROJECT_MANAGER','USER') ORDER BY r.project_id,r.role_key", companyId, userId);
        Map<Long, List<String>> projectRoles = new LinkedHashMap<>();
        for (var assignment : assigned) {
            long project = ((Number) assignment.get("project_id")).longValue();
            projectRoles.computeIfAbsent(project, ignored -> new ArrayList<>()).add((String) assignment.get("role_key"));
        }

        // Fetch grants for this company only. Dates are inclusive and evaluated in UTC consistently.
        var grants = db.queryForList("SELECT g.project_id, bool_or(g.timesheets) AS timesheets, bool_or(g.expenses) AS expenses " +
                "FROM moderator_grants g JOIN projects p ON p.id=g.project_id AND p.company_id=g.company_id " +
                "WHERE g.company_id=? AND g.moderator_user_id=? AND g.revoked_at IS NULL " +
                "AND g.starts_on <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date " +
                "AND g.ends_on >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date GROUP BY g.project_id", companyId, userId);
        Map<Long, Map<String,Object>> projectGrants = new LinkedHashMap<>();
        for (var grant : grants) {
            long id=((Number)grant.get("project_id")).longValue();
            boolean hours=hasModeratorGrant(id,userId,false),expenses=hasModeratorGrant(id,userId,true);
            if(hours||expenses)projectGrants.put(id,Map.of("timesheets",hours,"expenses",expenses));
        }

        // Company-level oversight can view summaries but does not create a project management/approval assignment.
        var visible = db.queryForList("SELECT id FROM projects WHERE company_id=? ORDER BY id", companyId);
        List<ScopedPermissions.Project> projects = new ArrayList<>();
        for (var row : visible) {
            long projectId = ((Number) row.get("id")).longValue();
            var scopedRoles = projectRoles.getOrDefault(projectId, List.of());
            var grant = projectGrants.getOrDefault(projectId, Map.of());
            if (!companyAdmin && !creator && scopedRoles.isEmpty() && grant.isEmpty()) continue;
            boolean admin = scopedRoles.contains("PROJECT_ADMIN");
            boolean manager = scopedRoles.contains("PROJECT_MANAGER");
            boolean submit = !admin && (manager || scopedRoles.contains("USER"));
            boolean reviewTime = admin || manager || Boolean.TRUE.equals(grant.get("timesheets"));
            boolean reviewExpense = admin || manager || Boolean.TRUE.equals(grant.get("expenses"));
            Map<String, Boolean> projectCapabilities = new LinkedHashMap<>();
            projectCapabilities.put("canViewProject", true);
            projectCapabilities.put("canManageProject", admin);
            projectCapabilities.put("canArchiveProject", companyAdmin || admin);
            projectCapabilities.put("canSubmitWork", submit);
            projectCapabilities.put("canReviewWork", reviewTime || reviewExpense);
            projectCapabilities.put("canReviewTime", reviewTime);
            projectCapabilities.put("canReviewExpenses", reviewExpense);
            // These are role eligibility flags, not authorization to approve a particular submission.
            projectCapabilities.put("reviewRequiresDifferentSubmitter", reviewTime || reviewExpense);
            projectCapabilities.put("timeFallbackRequiresReason", admin && !manager && !Boolean.TRUE.equals(grant.get("timesheets")));
            projectCapabilities.put("expenseFallbackRequiresReason", admin && !manager && !Boolean.TRUE.equals(grant.get("expenses")));
            projects.add(new ScopedPermissions.Project(projectId, List.copyOf(scopedRoles), Map.copyOf(projectCapabilities)));
        }
        var capabilities=new LinkedHashMap<>(companyCapabilities(companyAdmin,creator,projects));
        var sensitive=db.queryForList("SELECT DISTINCT permission FROM company_sensitive_grants WHERE company_id=? AND user_id=? AND revoked_at IS NULL AND starts_on<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND ends_on>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date",String.class,companyId,userId);
        capabilities.put("canManagePerformanceReviews",sensitive.contains("PERFORMANCE_REVIEW"));
        capabilities.put("canHandleConfidentialReports",sensitive.contains("CONFIDENTIAL_HANDLER"));
        capabilities.put("canManageSensitiveGrants",companyAdmin);
        return new ScopedPermissions.Company(companyId, List.copyOf(roles), Map.copyOf(capabilities), List.copyOf(projects));
    }

    private Map<String, Boolean> companyCapabilities(boolean admin, boolean creator, List<ScopedPermissions.Project> projects) {
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        for (String key : List.of("canManageCompanyPeople", "canAssignCompanyRoles", "canManageCompanySettings",
                "canManageLeavePolicy", "canManageCompanyAnnouncements", "canReviewLeaveAndLetters",
                "canViewCompanyReports", "canViewCompanyAudit")) capabilities.put(key, admin);
        capabilities.put("canCreateProjects", admin || creator);
        capabilities.put("canViewProjects", admin || creator || !projects.isEmpty());
        capabilities.put("canManageProjects", creator || projects.stream().anyMatch(p -> p.capabilities().get("canManageProject")));
        capabilities.put("canSubmitWork", projects.stream().anyMatch(p -> p.capabilities().get("canSubmitWork")));
        capabilities.put("canReviewWork", projects.stream().anyMatch(p -> p.capabilities().get("canReviewWork")));
        // Dedicated grants must be implemented before these can be enabled.
        capabilities.put("canManagePerformanceReviews", false);
        capabilities.put("canHandleConfidentialReports", false);
        capabilities.put("canViewRestrictedPersonalFields", false);
        return Map.copyOf(capabilities);
    }

    public void requireCompanyCapability(long companyId, long userId, String capability) {
        if (!Boolean.TRUE.equals(companyPermissions(companyId, userId).capabilities().get(capability)))
            throw new AccessDeniedException("Company permission required: " + capability);
    }

    public long companyId(long projectId) {
        Long id = db.queryForObject("SELECT company_id FROM projects WHERE id = ?", Long.class, projectId);
        if (id == null) throw new IllegalArgumentException("Project not found");
        return id;
    }

    public boolean hasPlatformRole(long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments WHERE user_id = ? AND role_key = ? " +
                "AND company_id IS NULL AND project_id IS NULL AND removed_at IS NULL)", userId, role);
    }

    public boolean hasCompanyRole(long companyId, long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN company_memberships m " +
                "ON m.company_id = r.company_id AND m.user_id = r.user_id " +
                "WHERE r.company_id = ? AND r.user_id = ? AND r.role_key = ? AND r.project_id IS NULL AND EXISTS(SELECT 1 FROM companies c WHERE c.id=r.company_id AND NOT c.is_suspended) " +
                "AND r.removed_at IS NULL AND m.status = 'ACTIVE')", companyId, userId, role);
    }

    public boolean hasProjectRole(long projectId, long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN projects p ON p.id = r.project_id " +
                "JOIN project_memberships pm ON pm.project_id = p.id AND pm.user_id = r.user_id " +
                "JOIN company_memberships cm ON cm.company_id = p.company_id AND cm.user_id = r.user_id " +
                "WHERE r.project_id = ? AND r.user_id = ? AND r.role_key = ? AND r.company_id = p.company_id AND EXISTS(SELECT 1 FROM companies c WHERE c.id=p.company_id AND NOT c.is_suspended) " +
                "AND r.removed_at IS NULL AND pm.status = 'ACTIVE' AND cm.status = 'ACTIVE')", projectId, userId, role);
    }

    public boolean mayManageProject(long projectId, long userId) {
        return !hasPlatformRole(userId,"PLATFORM_ADMIN") && hasProjectRole(projectId, userId, "PROJECT_ADMIN");
    }

    public boolean mayCreateProject(long companyId, long userId) {
        return !hasPlatformRole(userId,"PLATFORM_ADMIN") && activeAccount(userId)
            && (hasCompanyRole(companyId,userId,"COMPANY_ADMIN")||hasCompanyRole(companyId,userId,"PROJECT_ADMIN"));
    }

    public boolean hasAnyProjectRole(long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN project_memberships pm " +
                "ON pm.project_id = r.project_id AND pm.user_id = r.user_id " +
                "JOIN company_memberships cm ON cm.company_id=r.company_id AND cm.user_id=r.user_id " +
                "WHERE r.user_id = ? AND r.role_key = ? AND r.project_id IS NOT NULL " +
                "AND r.removed_at IS NULL AND pm.status = 'ACTIVE' AND cm.status='ACTIVE')", userId, role);
    }

    public boolean hasAnyCompanyRole(long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN company_memberships m " +
                "ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.user_id=? AND r.role_key=? " +
                "AND r.project_id IS NULL AND r.removed_at IS NULL AND m.status='ACTIVE')", userId, role);
    }

    public boolean hasAnyModeratorGrant(long userId) {
        return db.queryForList("SELECT DISTINCT project_id FROM moderator_grants WHERE moderator_user_id=? AND revoked_at IS NULL",Long.class,userId)
            .stream().anyMatch(id->hasModeratorGrant(id,userId,false)||hasModeratorGrant(id,userId,true));
    }
    public boolean maySubmit(long projectId, long userId) {
        return !hasPlatformRole(userId, "PLATFORM_ADMIN")
                && (hasProjectRole(projectId, userId, "USER") || hasProjectRole(projectId, userId, "PROJECT_MANAGER"))
                && !hasProjectRole(projectId, userId, "PROJECT_ADMIN");
    }

    public boolean hasSubmittableProject(long userId) {
        return db.queryForList("SELECT project_id FROM project_assignments WHERE user_id=? AND is_active=TRUE",
                Long.class, userId).stream().anyMatch(projectId -> maySubmit(projectId, userId));
    }

    public List<String> roleKeys(long userId) {
        return db.queryForList("SELECT DISTINCT role_key FROM role_assignments WHERE user_id=? AND removed_at IS NULL " +
                "ORDER BY role_key", String.class, userId);
    }

    public void requireMaySubmit(long projectId, long userId, String kind) {
        if (!maySubmit(projectId, userId))
            throw new AccessDeniedException("An active User role is required to submit " + kind +
                    "; Project Admins cannot submit on projects they administer");
    }

    public boolean hasModeratorGrant(long projectId, long userId, boolean expense) {
        String permission=expense ? "expenses" : "timesheets";
        return !hasPlatformRole(userId,"PLATFORM_ADMIN") && hasProjectRole(projectId,userId,"USER")
            && exists("SELECT EXISTS(SELECT 1 FROM moderator_grants g JOIN project_assignments a ON a.project_id=g.project_id AND a.user_id=g.moderator_user_id WHERE g.project_id=? AND g.moderator_user_id=? AND g."+permission+" AND g.revoked_at IS NULL AND a.is_active AND (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date BETWEEN g.starts_on AND g.ends_on AND (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date BETWEEN a.start_date AND a.end_date)",projectId,userId);
    }
    public boolean mayReview(long projectId, long reviewerId, long submitterId, boolean expense,
                             String fallbackReason) {
        if(hasPlatformRole(reviewerId,"PLATFORM_ADMIN"))return false;
        if (reviewerId == submitterId) return false;
        if (hasModeratorGrant(projectId, reviewerId, expense)) return true;
        if (hasProjectRole(projectId, submitterId, "PROJECT_MANAGER"))
            return hasProjectRole(projectId, reviewerId, "PROJECT_ADMIN");
        if (hasProjectRole(projectId, reviewerId, "PROJECT_MANAGER")) return true;
        return hasProjectRole(projectId, reviewerId, "PROJECT_ADMIN")
                && fallbackReason != null && !fallbackReason.isBlank() && fallbackReason.trim().length() <= 500;
    }

    public void requireMayReview(long projectId, long reviewerId, long submitterId, boolean expense,
                                 String fallbackReason) {
        if (!mayReview(projectId, reviewerId, submitterId, expense, fallbackReason))
            throw new AccessDeniedException("A different authorized reviewer is required; Project Admin fallback requires a reason");
    }

    public List<Long> companyIds(long userId) {
        return db.queryForList("SELECT company_id FROM company_memberships WHERE user_id = ? AND status = 'ACTIVE' " +
                "AND EXISTS(SELECT 1 FROM companies c WHERE c.id=company_id AND NOT c.is_suspended) ORDER BY company_id", Long.class, userId);
    }

    public Set<Long> directoryUserIds(long actorId) {
        if (hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            return Set.of(actorId);
        return db.queryForList("SELECT DISTINCT target.user_id FROM company_memberships own " +
                "JOIN company_memberships target ON target.company_id=own.company_id AND target.status='ACTIVE' " +
                "WHERE own.user_id=? AND own.status='ACTIVE' AND EXISTS(SELECT 1 FROM companies c WHERE c.id=own.company_id AND NOT c.is_suspended) AND (" +
                "EXISTS (SELECT 1 FROM role_assignments r WHERE r.user_id=? AND r.company_id=own.company_id " +
                "AND r.role_key IN ('COMPANY_ADMIN','PROJECT_ADMIN') AND r.project_id IS NULL AND r.removed_at IS NULL) " +
                "OR EXISTS (SELECT 1 FROM role_assignments r WHERE r.user_id=? AND r.company_id=own.company_id " +
                "AND r.role_key='PROJECT_ADMIN' AND r.project_id IS NOT NULL AND r.removed_at IS NULL " +
                "AND EXISTS (SELECT 1 FROM project_memberships pm JOIN project_memberships team ON team.project_id=pm.project_id " +
                "JOIN projects p ON p.id=pm.project_id AND p.company_id=r.company_id WHERE pm.project_id=r.project_id " +
                "AND pm.user_id=r.user_id AND pm.status='ACTIVE' AND team.user_id=target.user_id AND team.status='ACTIVE'))) ",
                Long.class, actorId, actorId, actorId).stream().collect(Collectors.toSet());
    }

    public void activateProjectRole(long companyId, long projectId, long userId, String role, long actorId) {
        lockCompanyAdministration(companyId);
        if(!exists("SELECT EXISTS(SELECT 1 FROM company_memberships WHERE company_id=? AND user_id=? AND status='ACTIVE')",companyId,userId))
            throw new AccessDeniedException("An active company membership is required before assigning project access");
        if(companyId(projectId)!=companyId)throw new IllegalArgumentException("Project company mismatch");
        db.update("INSERT INTO project_memberships(project_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now()) " +
                "ON CONFLICT (project_id,user_id) DO UPDATE SET status='ACTIVE',removed_at=NULL", projectId, userId);
        db.update("INSERT INTO role_assignments(user_id,role_key,company_id,project_id,assigned_by_user_id) " +
                "VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING", userId, role, companyId, projectId, actorId);
    }

    public void removeProjectRole(long projectId, long userId, String role) {
        db.update("UPDATE role_assignments SET removed_at=now() WHERE project_id=? AND user_id=? " +
                "AND role_key=? AND removed_at IS NULL", projectId, userId, role);
    }
}
