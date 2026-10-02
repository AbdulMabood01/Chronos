package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyManagementService {
    private static final Set<String> COMPANY_ROLES = Set.of("COMPANY_ADMIN", "PROJECT_ADMIN", "MODERATOR");
    private static final Set<String> PROJECT_ROLES = Set.of("PROJECT_ADMIN", "PROJECT_MANAGER", "USER");
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final UserRepository users;
    private final InvitationEmailService mail;
    private final AuditService audit;
    private final OnboardingService onboarding;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    public record InvitationDetails(long id, String companyName, String projectName, String role,
                                    String email, String status, OffsetDateTime expiresAt) {}
    private static final String INVITATION_DETAILS_SQL = "SELECT i.id, c.name AS company_name, " +
            "p.name AS project_name, i.role_key, i.invitee_email, i.expires_at, " +
            "CASE WHEN i.accepted_at IS NOT NULL THEN 'ACCEPTED' " +
            "WHEN i.revoked_at IS NOT NULL THEN 'REVOKED' " +
            "WHEN i.expires_at <= now() THEN 'EXPIRED' ELSE 'PENDING' END AS status " +
            "FROM company_invitations i JOIN companies c ON c.id=i.company_id " +
            "LEFT JOIN projects p ON p.id=i.project_id ";

    private InvitationDetails invitationDetails(ResultSet rs, int row) throws SQLException {
        return new InvitationDetails(rs.getLong("id"), rs.getString("company_name"),
                rs.getString("project_name"), rs.getString("role_key"), rs.getString("invitee_email"),
                rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class));
    }

    public InvitationDetails previewInvitation(String token) {
        return db.query(INVITATION_DETAILS_SQL + "WHERE i.token_hash=?", this::invitationDetails, hash(token))
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));
    }

    public List<InvitationDetails> myPendingInvitations(long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new AccessDeniedException("Sign in required"));
        return db.query(INVITATION_DETAILS_SQL + "WHERE i.invitee_email=? AND i.accepted_at IS NULL " +
                "AND i.revoked_at IS NULL AND i.expires_at > now() ORDER BY i.created_at DESC, i.id DESC",
                this::invitationDetails, actor.getEmail().toLowerCase(Locale.ROOT));
    }

    public List<Map<String, Object>> myCompanies(long actorId) {
        if (access.hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            return db.queryForList("SELECT id, name, slug, plan_tier, project_limit, team_limit FROM companies ORDER BY name");
        return db.queryForList("SELECT c.id, c.name, c.slug, c.plan_tier, c.project_limit, c.team_limit " +
                "FROM companies c JOIN company_memberships m ON m.company_id = c.id " +
                "WHERE m.user_id = ? AND m.status = 'ACTIVE' ORDER BY c.name", actorId);
    }

    public long createCompany(String name, String slug, long actorId) {
        if (!access.hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            throw new AccessDeniedException("Platform Admin permission required");
        if (name == null || name.isBlank() || name.length() > 150 || slug == null
                || !slug.matches("[a-z0-9][a-z0-9-]{1,78}"))
            throw new IllegalArgumentException("A company name and lowercase slug are required");
        Long id = db.queryForObject("INSERT INTO companies(name, slug) VALUES (?, ?) RETURNING id", Long.class,
                name.trim(), slug);
        audit.logAction(actorId, "COMPANY_CREATED", "Company", id, "Company created: " + slug);
        return id;
    }

    public List<Map<String, Object>> members(long companyId, long actorId) {
        if (!access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && !access.hasCompanyRole(companyId, actorId, "PROJECT_ADMIN")
                && !access.hasPlatformRole(actorId, "PLATFORM_ADMIN")
                && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM role_assignments " +
                "WHERE company_id=? AND user_id=? AND role_key='PROJECT_ADMIN' AND project_id IS NOT NULL " +
                "AND removed_at IS NULL)", Boolean.class, companyId, actorId)))
            throw new AccessDeniedException("Company roster permission required");
        return db.queryForList("SELECT m.user_id, u.email, u.first_name, u.last_name, m.status, " +
                "COALESCE(array_agg(DISTINCT r.role_key) FILTER (WHERE r.removed_at IS NULL), '{}') AS roles " +
                "FROM company_memberships m JOIN users u ON u.id = m.user_id " +
                "LEFT JOIN role_assignments r ON r.user_id = m.user_id AND r.company_id = m.company_id " +
                "AND r.project_id IS NULL WHERE m.company_id = ? GROUP BY m.user_id, u.email, u.first_name, " +
                "u.last_name, m.status ORDER BY u.email", companyId);
    }

    public void invite(long companyId, Long projectId, String address, String role, long actorId) {
        invite(companyId, projectId, address, role, actorId, null);
    }

    public void invite(long companyId, Long projectId, String address, String role, long actorId,
                       Long accessRequestId) {
        String normalized = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || normalized.length() > 255)
            throw new IllegalArgumentException("Valid invitee email is required");
        if (projectId == null && !COMPANY_ROLES.contains(role) || projectId != null && !PROJECT_ROLES.contains(role))
            throw new IllegalArgumentException("Role scope does not match invitation");
        if (projectId != null && access.companyId(projectId) != companyId)
            throw new IllegalArgumentException("Project does not belong to this company");
        if ("COMPANY_ADMIN".equals(role) && !access.hasPlatformRole(actorId, "PLATFORM_ADMIN")
                && !access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN"))
            throw new AccessDeniedException("Company Admin invitation permission required");
        if (projectId == null && !"COMPANY_ADMIN".equals(role)) requireCompanyAdmin(companyId, actorId);
        if (projectId != null && !access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && !access.mayManageProject(projectId, actorId))
            throw new AccessDeniedException("Project Admin invitation permission required");
        if (accessRequestId != null) {
            requireCompanyAdmin(companyId, actorId);
            if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM company_access_requests " +
                    "WHERE id=? AND company_id=? AND email=? AND invited_at IS NULL AND dismissed_at IS NULL)",
                    Boolean.class, accessRequestId, companyId, normalized)))
                throw new IllegalArgumentException("Pending access request not found for this email");
        }
        if ("USER".equals(role)) requireTeamCapacity(companyId, projectId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String companyName = db.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, companyId);
        OffsetDateTime expires = OffsetDateTime.now().plusDays(30);
        db.update("INSERT INTO company_invitations(company_id, project_id, invitee_email, role_key, token_hash, " +
                "created_by_user_id, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                companyId, projectId, normalized, role, hash(token), actorId, expires);
        mail.sendCompanyInvitation(normalized, companyName, token, expires.toInstant());
        if (accessRequestId != null) db.update("UPDATE company_access_requests SET invited_at=now() " +
                "WHERE id=? AND company_id=? AND email=? AND invited_at IS NULL AND dismissed_at IS NULL",
                accessRequestId, companyId, normalized);
        audit.logAction(actorId, "COMPANY_INVITED", "Company", companyId, "Invited " + normalized + " as " + role);
    }

    public void requestAccess(String slug, String firstName, String lastName, String address) {
        String normalizedSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        String email = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSlug.matches("[a-z0-9][a-z0-9-]{1,78}") || firstName == null
                || firstName.isBlank() || firstName.length() > 100 || lastName == null
                || lastName.isBlank() || lastName.length() > 100
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 255)
            throw new IllegalArgumentException("Enter a workspace ID, name and valid email address");
        List<Long> ids = db.queryForList("SELECT id FROM companies WHERE slug=?", Long.class, normalizedSlug);
        if (ids.isEmpty()) return;
        db.update("INSERT INTO company_access_requests(company_id,first_name,last_name,email) " +
                "VALUES (?,?,?,?) ON CONFLICT (company_id,email) WHERE invited_at IS NULL AND dismissed_at IS NULL " +
                "DO NOTHING", ids.get(0), firstName.trim(), lastName.trim(), email);
    }

    public List<Map<String, Object>> accessRequests(long companyId, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        return db.queryForList("SELECT id,first_name,last_name,email,requested_at FROM company_access_requests " +
                "WHERE company_id=? AND invited_at IS NULL AND dismissed_at IS NULL ORDER BY requested_at,id",
                companyId);
    }

    public void dismissAccessRequest(long companyId, long requestId, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        if (db.update("UPDATE company_access_requests SET dismissed_at=now() WHERE company_id=? AND id=? " +
                "AND invited_at IS NULL AND dismissed_at IS NULL", companyId, requestId) != 1)
            throw new IllegalArgumentException("Pending access request not found");
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Dismissed access request " + requestId);
    }

    public List<Map<String, Object>> invitations(long companyId, long actorId) {
        if (access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                || access.hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            return db.queryForList("SELECT id,project_id,invitee_email,role_key,created_at,expires_at,accepted_at,revoked_at " +
                    "FROM company_invitations WHERE company_id=? ORDER BY id DESC", companyId);
        return db.queryForList("SELECT i.id,i.project_id,i.invitee_email,i.role_key,i.created_at,i.expires_at, " +
                "i.accepted_at,i.revoked_at FROM company_invitations i JOIN role_assignments r " +
                "ON r.project_id=i.project_id AND r.company_id=i.company_id AND r.user_id=? " +
                "AND r.role_key='PROJECT_ADMIN' AND r.removed_at IS NULL " +
                "WHERE i.company_id=? ORDER BY i.id DESC", actorId, companyId);
    }

    public void revokeInvitation(long companyId, long invitationId, long actorId) {
        Long projectId = db.queryForObject("SELECT project_id FROM company_invitations WHERE id=? AND company_id=?",
                Long.class, invitationId, companyId);
        if (!access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && !access.hasPlatformRole(actorId, "PLATFORM_ADMIN")
                && (projectId == null || !access.mayManageProject(projectId, actorId)))
            throw new AccessDeniedException("Invitation management permission required");
        int changed = db.update("UPDATE company_invitations SET revoked_at=now() WHERE id=? AND company_id=? " +
                "AND accepted_at IS NULL AND revoked_at IS NULL", invitationId, companyId);
        if (changed != 1) throw new IllegalArgumentException("Pending invitation not found");
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Revoked invitation " + invitationId);
    }

    public void accept(String token, long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new AccessDeniedException("Sign in required"));
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE token_hash = ? " +
                "FOR UPDATE", hash(token)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));
        acceptInvitation(invitation, actor, actorId);
    }

    public String claimInvitation(String token, String firstName, String lastName, String password) {
        OnboardingService.validatePassword(password);
        if (firstName == null || firstName.isBlank() || firstName.length() > 100
                || lastName == null || lastName.isBlank() || lastName.length() > 100)
            throw new IllegalArgumentException("First and last name are required");
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE token_hash=? " +
                "FOR UPDATE", hash(token)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));
        if (invitation.get("accepted_at") != null || invitation.get("revoked_at") != null
                || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT expires_at > now() FROM company_invitations WHERE id=?", Boolean.class, invitation.get("id"))))
            throw new IllegalArgumentException("Invitation has expired or is no longer valid");
        String email = (String) invitation.get("invitee_email");
        User actor = users.findByEmailForUpdate(email).orElse(null);
        if (actor != null && actor.getPasswordHash() != null)
            throw new IllegalArgumentException("This email already has an account. Sign in to accept the invitation.");
        if (actor != null && !Boolean.TRUE.equals(actor.getIsActive()))
            throw new IllegalArgumentException("This account is inactive. Contact your administrator.");
        if (actor == null) actor = onboarding.create(firstName, lastName, email);
        actor.setPasswordHash(passwords.encode(password));
        users.saveAndFlush(actor);
        acceptInvitation(invitation, actor, actor.getId());
        return email;
    }

    public void acceptById(long invitationId, long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new AccessDeniedException("Sign in required"));
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE id=? " +
                "AND invitee_email=? FOR UPDATE", invitationId, actor.getEmail().toLowerCase(Locale.ROOT))
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Invitation not found for this account"));
        acceptInvitation(invitation, actor, actorId);
    }

    private void acceptInvitation(Map<String, Object> invitation, User actor, long actorId) {
        if (invitation.get("accepted_at") != null || invitation.get("revoked_at") != null
                || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT expires_at > now() FROM company_invitations WHERE id = ?", Boolean.class, invitation.get("id"))))
            throw new IllegalArgumentException("Invitation has expired or is no longer valid");
        if (!actor.getEmail().equalsIgnoreCase((String) invitation.get("invitee_email")))
            throw new AccessDeniedException("Sign in using the invited email address");
        long companyId = ((Number) invitation.get("company_id")).longValue();
        Long projectId = invitation.get("project_id") == null ? null
                : ((Number) invitation.get("project_id")).longValue();
        String role = (String) invitation.get("role_key");
        if (projectId != null && access.companyId(projectId) != companyId)
            throw new IllegalArgumentException("Invitation project does not belong to its company");
        // A pending invitation already reserves this team slot.
        db.update("INSERT INTO company_memberships(company_id, user_id, status, joined_at) " +
                "VALUES (?, ?, 'ACTIVE', now()) ON CONFLICT (company_id, user_id) " +
                "DO UPDATE SET status = 'ACTIVE', joined_at = now(), removed_at = NULL", companyId, actorId);
        if (projectId != null)
            db.update("INSERT INTO project_memberships(project_id, user_id, status, joined_at) " +
                    "VALUES (?, ?, 'ACTIVE', now()) ON CONFLICT (project_id, user_id) " +
                    "DO UPDATE SET status = 'ACTIVE', joined_at = now(), removed_at = NULL", projectId, actorId);
        db.update("INSERT INTO role_assignments(user_id, role_key, company_id, project_id, assigned_by_user_id) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", actorId, role, companyId, projectId,
                invitation.get("created_by_user_id"));
        db.update("UPDATE company_invitations SET accepted_at = now() WHERE id = ?", invitation.get("id"));
        audit.logAction(actorId, "COMPANY_INVITE_ACCEPTED", "Company", companyId, "Accepted " + role + " invitation");
    }

    public void grantModerator(long companyId, long projectId, long moderatorId, boolean timesheets,
                               boolean expenses, LocalDate startsOn, LocalDate endsOn, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        if (access.companyId(projectId) != companyId) throw new IllegalArgumentException("Project company mismatch");
        if (!access.hasCompanyRole(companyId, moderatorId, "MODERATOR"))
            throw new IllegalArgumentException("The Moderator must first accept their company invitation");
        if (!timesheets && !expenses || startsOn == null || endsOn == null || startsOn.isAfter(endsOn))
            throw new IllegalArgumentException("Choose an approval type and valid date range");
        db.update("INSERT INTO moderator_grants(company_id, project_id, moderator_user_id, timesheets, expenses, " +
                "starts_on, ends_on, granted_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                companyId, projectId, moderatorId, timesheets, expenses, startsOn, endsOn, actorId);
        audit.logAction(actorId, "ROLE_CHANGED", "Project", projectId, "Granted time-limited Moderator approvals");
    }

    public List<Map<String, Object>> moderatorGrants(long companyId, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        return db.queryForList("SELECT g.id,g.project_id,p.name AS project_name,g.moderator_user_id,u.email, " +
                "g.timesheets,g.expenses,g.starts_on,g.ends_on,g.revoked_at FROM moderator_grants g " +
                "JOIN projects p ON p.id=g.project_id JOIN users u ON u.id=g.moderator_user_id " +
                "WHERE g.company_id=? ORDER BY g.id DESC", companyId);
    }

    public void revokeModerator(long companyId, long grantId, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        int changed = db.update("UPDATE moderator_grants SET revoked_at = now() WHERE id = ? AND company_id = ? " +
                "AND revoked_at IS NULL", grantId, companyId);
        if (changed != 1) throw new IllegalArgumentException("Active Moderator grant not found");
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Revoked Moderator grant " + grantId);
    }

    public void removeRole(long companyId, Long projectId, long targetId, String role, long actorId) {
        if (projectId == null && "COMPANY_ADMIN".equals(role)) {
            Integer admins = db.queryForObject("SELECT count(*) FROM role_assignments WHERE company_id=? " +
                    "AND project_id IS NULL AND role_key='COMPANY_ADMIN' AND removed_at IS NULL", Integer.class, companyId);
            if (admins != null && admins <= 1)
                throw new IllegalArgumentException("Appoint another Company Admin before removing the last one");
        }
        if (projectId != null && "PROJECT_ADMIN".equals(role)
                && Boolean.TRUE.equals(db.queryForObject("SELECT owner_user_id=? FROM projects WHERE id=?", Boolean.class,
                targetId, projectId)))
            throw new IllegalArgumentException("Transfer project ownership before removing its owner");
        if (projectId != null && "PROJECT_ADMIN".equals(role)
                && Boolean.TRUE.equals(db.queryForObject("SELECT project_manager_hours_approver_id=? FROM projects WHERE id=?",
                Boolean.class, targetId, projectId)))
            throw new IllegalArgumentException("Choose a different PM hours approver before removing this Project Admin");
        if (projectId != null && "PROJECT_MANAGER".equals(role)
                && Boolean.TRUE.equals(db.queryForObject("SELECT project_manager_id=? FROM projects WHERE id=?",
                Boolean.class, targetId, projectId)))
            throw new IllegalArgumentException("Choose a different primary Project Manager before removing this role");
        if (projectId != null && access.companyId(projectId) != companyId)
            throw new IllegalArgumentException("Project company mismatch");
        if (projectId == null || "PROJECT_ADMIN".equals(role)) requireCompanyAdmin(companyId, actorId);
        else if (!access.mayManageProject(projectId, actorId))
            throw new AccessDeniedException("Project Admin permission required");
        int changed = projectId == null
                ? db.update("UPDATE role_assignments SET removed_at = now() WHERE user_id = ? AND role_key = ? " +
                        "AND company_id = ? AND project_id IS NULL AND removed_at IS NULL", targetId, role, companyId)
                : db.update("UPDATE role_assignments SET removed_at = now() WHERE user_id = ? AND role_key = ? " +
                        "AND company_id = ? AND project_id = ? AND removed_at IS NULL", targetId, role, companyId, projectId);
        if (changed == 0) throw new IllegalArgumentException("Active role assignment not found");
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Removed " + role + " from user " + targetId);
    }

    public void transferProjectOwnership(long companyId, long projectId, long newOwnerId, long actorId) {
        requireCompanyAdmin(companyId, actorId);
        if (access.companyId(projectId) != companyId || !access.hasProjectRole(projectId, newOwnerId, "PROJECT_ADMIN"))
            throw new IllegalArgumentException("Choose an appointed Project Admin from this project");
        db.update("UPDATE projects SET owner_user_id=? WHERE id=? AND company_id=?", newOwnerId, projectId, companyId);
        audit.logAction(actorId, "ROLE_CHANGED", "Project", projectId, "Transferred project ownership to user " + newOwnerId);
    }

    public List<Map<String, Object>> projectRoles(long companyId, long projectId, long actorId) {
        if (access.companyId(projectId) != companyId || !access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && !access.hasPlatformRole(actorId, "PLATFORM_ADMIN")
                && !access.mayManageProject(projectId, actorId))
            throw new AccessDeniedException("Project role view permission required");
        return db.queryForList("SELECT r.user_id,u.email,r.role_key,p.owner_user_id=r.user_id AS owner " +
                "FROM role_assignments r JOIN users u ON u.id=r.user_id JOIN projects p ON p.id=r.project_id " +
                "WHERE r.company_id=? AND r.project_id=? AND r.removed_at IS NULL ORDER BY r.role_key,u.email",
                companyId, projectId);
    }

    private void requireCompanyAdmin(long companyId, long actorId) {
        if (!access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && !access.hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            throw new AccessDeniedException("Company Admin permission required");
    }

    private void requireTeamCapacity(long companyId, long projectId) {
        db.queryForObject("SELECT id FROM companies WHERE id=? FOR UPDATE", Long.class, companyId);
        Integer limit = db.queryForObject("SELECT team_limit FROM companies WHERE id = ?", Integer.class, companyId);
        Integer active = db.queryForObject("SELECT count(*) FROM project_memberships WHERE project_id = ? " +
                "AND status = 'ACTIVE'", Integer.class, projectId);
        Integer pending = db.queryForObject("SELECT count(*) FROM company_invitations WHERE project_id = ? " +
                "AND role_key = 'USER' AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > now()",
                Integer.class, projectId);
        if (active + pending >= limit) throw new IllegalArgumentException("Team size limit reached for this company tier");
    }

    private String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid invitation");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
