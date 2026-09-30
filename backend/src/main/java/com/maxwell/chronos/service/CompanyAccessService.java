package com.maxwell.chronos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
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
                "WHERE r.company_id = ? AND r.user_id = ? AND r.role_key = ? AND r.project_id IS NULL " +
                "AND r.removed_at IS NULL AND m.status = 'ACTIVE')", companyId, userId, role);
    }

    public boolean hasProjectRole(long projectId, long userId, String role) {
        return exists("SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN projects p ON p.id = r.project_id " +
                "JOIN project_memberships pm ON pm.project_id = p.id AND pm.user_id = r.user_id " +
                "JOIN company_memberships cm ON cm.company_id = p.company_id AND cm.user_id = r.user_id " +
                "WHERE r.project_id = ? AND r.user_id = ? AND r.role_key = ? AND r.company_id = p.company_id " +
                "AND r.removed_at IS NULL AND pm.status = 'ACTIVE' AND cm.status = 'ACTIVE')", projectId, userId, role);
    }

    public boolean mayManageProject(long projectId, long userId) {
        return hasProjectRole(projectId, userId, "PROJECT_ADMIN");
    }

    public boolean mayCreateProject(long companyId, long userId) {
        return hasCompanyRole(companyId, userId, "PROJECT_ADMIN");
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
        return exists("SELECT EXISTS (SELECT 1 FROM moderator_grants g JOIN role_assignments r " +
                "ON r.user_id = g.moderator_user_id AND r.company_id = g.company_id AND r.role_key = 'MODERATOR' " +
                "JOIN company_memberships cm ON cm.company_id = g.company_id AND cm.user_id = g.moderator_user_id " +
                "WHERE g.moderator_user_id = ? AND g.revoked_at IS NULL AND r.removed_at IS NULL " +
                "AND cm.status = 'ACTIVE' AND g.starts_on <= CURRENT_DATE AND g.ends_on >= CURRENT_DATE)", userId);
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
        String permission = expense ? "expenses" : "timesheets";
        return hasCompanyRole(companyId(projectId), userId, "MODERATOR")
                && exists("SELECT EXISTS (SELECT 1 FROM moderator_grants g JOIN projects p ON p.id = g.project_id " +
                "WHERE g.project_id = ? AND g.moderator_user_id = ? AND g.company_id = p.company_id " +
                "AND g." + permission + " = TRUE AND g.revoked_at IS NULL " +
                "AND g.starts_on <= ? AND g.ends_on >= ?)", projectId, userId, LocalDate.now(), LocalDate.now());
    }

    public boolean mayReview(long projectId, long reviewerId, long submitterId, boolean expense,
                             String fallbackReason) {
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
                "ORDER BY company_id", Long.class, userId);
    }

    public Set<Long> directoryUserIds(long actorId) {
        if (hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            return db.queryForList("SELECT id FROM users", Long.class).stream().collect(Collectors.toSet());
        return db.queryForList("SELECT DISTINCT target.user_id FROM company_memberships own " +
                "JOIN company_memberships target ON target.company_id=own.company_id AND target.status='ACTIVE' " +
                "WHERE own.user_id=? AND own.status='ACTIVE' AND (" +
                "EXISTS (SELECT 1 FROM role_assignments r WHERE r.user_id=? AND r.company_id=own.company_id " +
                "AND r.role_key IN ('COMPANY_ADMIN','PROJECT_ADMIN') AND r.project_id IS NULL AND r.removed_at IS NULL) " +
                "OR EXISTS (SELECT 1 FROM role_assignments r WHERE r.user_id=? AND r.company_id=own.company_id " +
                "AND r.role_key='PROJECT_ADMIN' AND r.project_id IS NOT NULL AND r.removed_at IS NULL " +
                "AND EXISTS (SELECT 1 FROM project_memberships pm WHERE pm.project_id=r.project_id " +
                "AND pm.user_id=r.user_id AND pm.status='ACTIVE'))) ",
                Long.class, actorId, actorId, actorId).stream().collect(Collectors.toSet());
    }

    public void activateProjectRole(long companyId, long projectId, long userId, String role, long actorId) {
        db.update("INSERT INTO company_memberships(company_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now()) " +
                "ON CONFLICT (company_id,user_id) DO UPDATE SET status='ACTIVE',removed_at=NULL", companyId, userId);
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
