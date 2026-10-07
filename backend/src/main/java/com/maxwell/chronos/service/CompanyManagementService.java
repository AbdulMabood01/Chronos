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
    private static final Set<String> COMPANY_ROLES = Set.of("COMPANY_ADMIN", "PROJECT_ADMIN");
    private static final Set<String> PROJECT_ROLES = Set.of("PROJECT_ADMIN", "PROJECT_MANAGER", "USER");
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final UserRepository users;
    private final InvitationDeliveryService delivery;
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
            "WHEN i.expires_at <= now() THEN 'EXPIRED' WHEN c.is_suspended THEN 'SUSPENDED' ELSE 'PENDING' END AS status " +
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
            return db.queryForList("SELECT id, name, slug, plan_tier, project_limit, team_limit,is_suspended,platform_version FROM companies ORDER BY name");
        return db.queryForList("SELECT c.id, c.name, c.slug, c.plan_tier, c.project_limit, c.team_limit " +
                "FROM companies c JOIN company_memberships m ON m.company_id = c.id " +
                "WHERE m.user_id = ? AND m.status = 'ACTIVE' AND NOT c.is_suspended ORDER BY c.name", actorId);
    }

    public record CompanyContext(boolean platformAdmin, List<Map<String, Object>> memberships,
                                 List<Map<String, Object>> companies, com.maxwell.chronos.dto.ScopedPermissions.Platform platformPermissions) {}

    public CompanyContext companyContext(long actorId) {
        boolean platform = access.hasPlatformRole(actorId, "PLATFORM_ADMIN");
        var memberships = db.queryForList("SELECT c.id, c.name, c.slug, c.plan_tier, c.project_limit, c.team_limit " +
                "FROM companies c JOIN company_memberships m ON m.company_id=c.id " +
                "WHERE m.user_id=? AND m.status='ACTIVE' AND NOT c.is_suspended ORDER BY c.name, c.id", actorId);
        return new CompanyContext(platform, memberships, platform ? myCompanies(actorId) : memberships, access.platformPermissions(actorId));
    }

    public Map<String, Object> validateCompanyContext(long companyId, long actorId) {
        // Platform selection is company metadata administration, not an operational membership.
        boolean platform = access.hasPlatformRole(actorId, "PLATFORM_ADMIN");
        var rows = platform
                ? db.queryForList("SELECT id, name, slug, plan_tier, project_limit, team_limit,is_suspended,platform_version FROM companies WHERE id=?", companyId)
                : db.queryForList("SELECT c.id, c.name, c.slug, c.plan_tier, c.project_limit, c.team_limit " +
                    "FROM companies c JOIN company_memberships m ON m.company_id=c.id " +
                    "WHERE c.id=? AND m.user_id=? AND m.status='ACTIVE'", companyId, actorId);
        if (rows.isEmpty()) throw new org.springframework.security.access.AccessDeniedException(
                "You no longer have access to this company. Choose another workspace.");
        var company = new java.util.LinkedHashMap<>(rows.get(0));
        company.put("permissions", access.companyPermissions(companyId, actorId));
        return company;
    }

    public long createCompany(String name, String slug, String adminEmail, long actorId) {
        if (!access.hasPlatformRole(actorId, "PLATFORM_ADMIN"))
            throw new AccessDeniedException("Platform Admin permission required");
        String normalizedName = name == null ? "" : name.trim();
        String normalizedSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank() || normalizedName.length() > 150)
            throw new IllegalArgumentException("Enter a company name of 1 to 150 characters.");
        if (!normalizedSlug.matches("[a-z0-9][a-z0-9-]{1,78}"))
            throw new IllegalArgumentException("Workspace ID must be 2 to 79 characters using letters, numbers and hyphens, starting with a letter or number.");
        if (!validEmail(normalizedEmail))
            throw new IllegalArgumentException("Enter a valid initial company admin email address.");
        Long id;
        try {
            id = db.queryForObject("INSERT INTO companies(name, slug) VALUES (?, ?) ON CONFLICT (slug) DO NOTHING RETURNING id",
                    Long.class, normalizedName, normalizedSlug);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "That workspace ID is already in use. Choose another.");
        }
        audit.logAction(actorId, "COMPANY_CREATED", "Company", id, "Company created: " + normalizedSlug);
        // Company, initial admin invitation and encrypted delivery job commit together.
        invite(id, null, normalizedEmail, "COMPANY_ADMIN", actorId);
        db.update("INSERT INTO platform_activity(user_id,company_id,action) VALUES (?,?,'COMPANY_CREATED')",actorId,id);
        return id;
    }

    public List<Map<String, Object>> members(long companyId, long actorId) {
        var permissions=access.companyPermissions(companyId,actorId);
        boolean admin=Boolean.TRUE.equals(permissions.capabilities().get("canManageCompanyPeople"));
        boolean coordinator=permissions.companyRoles().contains("PROJECT_ADMIN");
        var managed=permissions.projects().stream().filter(project->project.roles().contains("PROJECT_ADMIN")).map(project->project.projectId()).toList();
        if(!admin && !coordinator && managed.isEmpty())
            throw new AccessDeniedException("Company roster permission required");
        var members = db.queryForList("SELECT m.user_id, u.email, u.first_name, u.last_name, m.status, " +
                "m.employee_id,m.job_title,m.joining_date,m.employment_version,m.membership_version, " +
                "(u.is_active AND u.password_hash IS NOT NULL AND NOT u.admin_locked) AS account_available, " +
                "EXISTS(SELECT 1 FROM role_assignments platform WHERE platform.user_id=u.id AND platform.role_key='PLATFORM_ADMIN' " +
                "AND platform.company_id IS NULL AND platform.project_id IS NULL AND platform.removed_at IS NULL) AS platform_account, " +
                "COALESCE(array_agg(DISTINCT r.role_key) FILTER (WHERE r.removed_at IS NULL), '{}') AS roles " +
                "FROM company_memberships m JOIN users u ON u.id = m.user_id " +
                "LEFT JOIN role_assignments r ON r.user_id = m.user_id AND r.company_id = m.company_id " +
                "AND r.project_id IS NULL WHERE m.company_id = ? GROUP BY m.id, u.id, m.user_id, u.email, u.first_name, " +
                "u.last_name, m.status ORDER BY u.email", companyId);
        for(var member:members) {
            if(member.get("roles") instanceof java.sql.Array sqlRoles) {
                try { member.put("roles",java.util.Arrays.asList((Object[])sqlRoles.getArray()));sqlRoles.free(); }
                catch(java.sql.SQLException ex) { throw new IllegalStateException("Unable to read company roles"); }
            }
            if(member.get("joining_date") instanceof java.sql.Date date) member.put("joining_date",date.toLocalDate());
        }
        if(admin)return members;
        var visible=coordinator ? java.util.Set.<Long>of() : new java.util.HashSet<>(db.queryForList(
                "SELECT DISTINCT pm.user_id FROM project_memberships pm JOIN projects p ON p.id=pm.project_id " +
                "WHERE p.company_id=? AND pm.status='ACTIVE' AND pm.project_id=ANY(?)",Long.class,companyId,managed.toArray(Long[]::new)));
        return members.stream().filter(member->"ACTIVE".equals(member.get("status")) &&
                (coordinator || visible.contains(((Number)member.get("user_id")).longValue()))).map(member->{
            member.remove("joining_date");member.remove("employment_version");member.remove("membership_version");
            member.remove("account_available");member.remove("platform_account");return member;
        }).toList();
    }

    public List<Map<String,Object>> members(long company,long actor,String status,String query) {
        if(status!=null && !Set.of("ALL","ACTIVE","PENDING","REMOVED").contains(status))throw new IllegalArgumentException("Choose a valid membership status");
        if(query!=null && query.length()>100)throw new IllegalArgumentException("Search must be at most 100 characters");
        String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        return members(company,actor).stream().filter(member->status==null||"ALL".equals(status)||status.equals(member.get("status")))
            .filter(member->search.isEmpty() || java.util.stream.Stream.of("email","first_name","last_name","employee_id","job_title")
                .anyMatch(field->java.util.Objects.toString(member.get(field),"").toLowerCase(Locale.ROOT).contains(search))).toList();
    }

    public void invite(long companyId, Long projectId, String address, String role, long actorId) {
        invite(companyId, projectId, address, role, actorId, null);
    }

    public void invite(long companyId, Long projectId, String address, String role, long actorId,
                       Long accessRequestId) {
        String normalized = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (!validEmail(normalized))
            throw new IllegalArgumentException("Valid invitee email is required");
        if (projectId == null && !COMPANY_ROLES.contains(role) || projectId != null && !PROJECT_ROLES.contains(role))
            throw new IllegalArgumentException("Role scope does not match invitation");
        if (projectId != null && access.companyId(projectId) != companyId)
            throw new IllegalArgumentException("Project does not belong to this company");
        access.lockCompanyAdministration(companyId);access.requireCompanyAvailable(companyId);
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN"))requireInitialCompanyOnboarding(companyId);
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN") && (projectId!=null || !"COMPANY_ADMIN".equals(role)))
            throw new AccessDeniedException("Company invitation management permission required");
        if("PROJECT_ADMIN".equals(role))requireCompanyAdmin(companyId,actorId);
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
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users u JOIN role_assignments r ON r.user_id=u.id " +
                "WHERE lower(u.email)=? AND r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.project_id IS NULL AND r.removed_at IS NULL)",Boolean.class,normalized)))
            throw new IllegalArgumentException("Choose an email address for a company member account");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id " +
                "WHERE m.company_id=? AND lower(u.email)=? AND m.status='REMOVED')",Boolean.class,companyId,normalized)))
            throw new IllegalArgumentException("Reactivate this company membership before sending a new role invitation");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String companyName = db.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, companyId);
        OffsetDateTime expires = OffsetDateTime.now().plusDays(30);
        Long invitationId=db.queryForObject("INSERT INTO company_invitations(company_id, project_id, invitee_email, role_key, token_hash, " +
                "created_by_user_id, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",Long.class,
                companyId, projectId, normalized, role, hash(token), actorId, expires);
        delivery.company(invitationId,token);
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
                || !validEmail(email))
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
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN"))throw new AccessDeniedException("Company invitation management permission required");
        if (access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN"))
            return db.queryForList("SELECT id,project_id,invitee_email,role_key,created_at,expires_at,accepted_at,revoked_at,(SELECT status FROM invitation_delivery d WHERE d.company_invitation_id=company_invitations.id ORDER BY d.id DESC LIMIT 1) delivery_status " +
                    "FROM company_invitations WHERE company_id=? ORDER BY id DESC", companyId);
        return db.queryForList("SELECT i.id,i.project_id,i.invitee_email,i.role_key,i.created_at,i.expires_at, " +
                "i.accepted_at,i.revoked_at,(SELECT status FROM invitation_delivery d WHERE d.company_invitation_id=i.id ORDER BY d.id DESC LIMIT 1) delivery_status FROM company_invitations i JOIN role_assignments r " +
                "ON r.project_id=i.project_id AND r.company_id=i.company_id AND r.user_id=? " +
                "AND r.role_key='PROJECT_ADMIN' AND r.removed_at IS NULL " +
                "WHERE i.company_id=? ORDER BY i.id DESC", actorId, companyId);
    }

    public void revokeInvitation(long companyId, long invitationId, long actorId) {
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN"))throw new AccessDeniedException("Company invitation management permission required");
        access.lockCompanyAdministration(companyId);
        Long projectId = db.queryForObject("SELECT project_id FROM company_invitations WHERE id=? AND company_id=?",
                Long.class, invitationId, companyId);
        if (!access.hasCompanyRole(companyId, actorId, "COMPANY_ADMIN")
                && (projectId == null || !access.mayManageProject(projectId, actorId)))
            throw new AccessDeniedException("Invitation management permission required");
        int changed = db.update("UPDATE company_invitations SET revoked_at=now() WHERE id=? AND company_id=? " +
                "AND accepted_at IS NULL AND revoked_at IS NULL", invitationId, companyId);
        if (changed != 1) throw new IllegalArgumentException("Pending invitation not found");
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Revoked invitation " + invitationId);
    }

    public void revokePlatformAdminInvitation(long company,long invitation,long actor){
        if(!access.hasPlatformRole(actor,"PLATFORM_ADMIN"))throw new AccessDeniedException("Platform Admin permission required");
        access.lockCompanyAdministration(company);
        requireInitialCompanyOnboarding(company);
        if(db.update("UPDATE company_invitations SET revoked_at=now() WHERE company_id=? AND id=? AND role_key='COMPANY_ADMIN' AND project_id IS NULL AND accepted_at IS NULL AND revoked_at IS NULL",company,invitation)!=1)throw new IllegalArgumentException("Pending invitation not found");
        db.update("UPDATE invitation_delivery SET status='CANCELLED',encrypted_token=NULL WHERE company_invitation_id=? AND status IN ('PENDING','FAILED')",invitation);
        db.update("INSERT INTO platform_activity(user_id,company_id,action) VALUES (?,?,'ADMIN_INVITATION_REVOKED')",actor,company);
    }
    public void resendInvitation(long company,long invitation,long actor){
        access.lockCompanyAdministration(company);access.requireCompanyAvailable(company);
        var rows=db.queryForList("SELECT * FROM company_invitations WHERE company_id=? AND id=? FOR UPDATE",company,invitation);
        if(rows.isEmpty())throw new IllegalArgumentException("Invitation not found");var row=rows.getFirst();
        if(row.get("accepted_at")!=null||row.get("revoked_at")!=null)throw new IllegalArgumentException("Create a new invitation for an accepted or revoked invitation");
        Long project=row.get("project_id")==null?null:((Number)row.get("project_id")).longValue();String role=(String)row.get("role_key");
        if(access.hasPlatformRole(actor,"PLATFORM_ADMIN")){if(project!=null||!role.equals("COMPANY_ADMIN"))throw new AccessDeniedException("Company invitation management permission required");}
        if(access.hasPlatformRole(actor,"PLATFORM_ADMIN"))requireInitialCompanyOnboarding(company);
        else if(!access.hasCompanyRole(company,actor,"COMPANY_ADMIN")&&(project==null||!access.mayManageProject(project,actor)||role.equals("PROJECT_ADMIN")))throw new AccessDeniedException("Invitation management permission required");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM invitation_delivery WHERE company_invitation_id=? AND created_at>now()-interval '1 minute')",Boolean.class,invitation)))throw new IllegalArgumentException("Please wait one minute before resending an invitation");
        if(project!=null&&role.equals("USER")&&Boolean.TRUE.equals(db.queryForObject("SELECT expires_at<=now() FROM company_invitations WHERE id=?",Boolean.class,invitation)))requireTeamCapacity(company,project);
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("UPDATE company_invitations SET token_hash=?,expires_at=now()+interval '30 days' WHERE id=?",hash(token),invitation);delivery.company(invitation,token);
        if(access.hasPlatformRole(actor,"PLATFORM_ADMIN"))db.update("INSERT INTO platform_activity(user_id,company_id,action) VALUES (?,?,'ADMIN_INVITATION_RESENT')",actor,company);
        else audit.logRequiredAction(actor,com.maxwell.chronos.enums.AuditAction.COMPANY_INVITED,"Company",company,"Company invitation resent");
    }
    private void requireInitialCompanyOnboarding(long company){
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.company_id=? AND r.project_id IS NULL AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE')",Boolean.class,company)))throw new AccessDeniedException("Company Admin onboarding is complete. Manage additional administrators within the company.");
    }

    public void accept(String token, long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new AccessDeniedException("Sign in required"));
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE token_hash = ?", hash(token)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));
        acceptInvitation(invitation, actor, actorId);
    }

    public String claimInvitation(String token, String firstName, String lastName, String password) {
        OnboardingService.validatePassword(password);
        if (firstName == null || firstName.isBlank() || firstName.length() > 100
                || lastName == null || lastName.isBlank() || lastName.length() > 100)
            throw new IllegalArgumentException("First and last name are required");
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE token_hash=?", hash(token)).stream().findFirst()
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
        if(actor!=null&&actor.isAdminLocked())throw new AccessDeniedException("This account is locked. Contact your administrator.");
        if (actor == null) actor = onboarding.create(firstName, lastName, email);
        actor.setPasswordHash(passwords.encode(password));
        users.saveAndFlush(actor);
        acceptInvitation(invitation, actor, actor.getId());
        return email;
    }

    public void acceptById(long invitationId, long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new AccessDeniedException("Sign in required"));
        Map<String, Object> invitation = db.queryForList("SELECT * FROM company_invitations WHERE id=? " +
                "AND invitee_email=?", invitationId, actor.getEmail().toLowerCase(Locale.ROOT))
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Invitation not found for this account"));
        acceptInvitation(invitation, actor, actorId);
    }

    private void acceptInvitation(Map<String, Object> invitation, User actor, long actorId) {
        if(access.hasPlatformRole(actor.getId(),"PLATFORM_ADMIN"))throw new AccessDeniedException("Platform administrator accounts cannot accept company operational roles");
        if(!actor.getEmail().equalsIgnoreCase((String)invitation.get("invitee_email")))throw new AccessDeniedException("Sign in using the invited email address");
        access.lockAccountAdministration(actor.getId());
        access.lockCompanyAdministration(((Number)invitation.get("company_id")).longValue());
        access.requireAvailableAccount(actor.getId());
        if(access.hasPlatformRole(actor.getId(),"PLATFORM_ADMIN"))throw new AccessDeniedException("Platform administrator accounts cannot accept company operational roles");
        invitation=db.queryForList("SELECT * FROM company_invitations WHERE id=? AND invitee_email=? FOR UPDATE",
                invitation.get("id"),actor.getEmail().toLowerCase(Locale.ROOT)).stream().findFirst()
                .orElseThrow(()->new IllegalArgumentException("Invitation not found for this account"));
        if (invitation.get("accepted_at") != null || invitation.get("revoked_at") != null
                || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT expires_at > now() FROM company_invitations WHERE id = ?", Boolean.class, invitation.get("id"))))
            throw new IllegalArgumentException("Invitation has expired or is no longer valid");
        if (!actor.getEmail().equalsIgnoreCase((String) invitation.get("invitee_email")))
            throw new AccessDeniedException("Sign in using the invited email address");
        long companyId = ((Number) invitation.get("company_id")).longValue();
        access.requireCompanyAvailable(companyId);
        Long projectId = invitation.get("project_id") == null ? null
                : ((Number) invitation.get("project_id")).longValue();
        String role = (String) invitation.get("role_key");
        if (projectId != null && access.companyId(projectId) != companyId)
            throw new IllegalArgumentException("Invitation project does not belong to its company");
        // A pending invitation already reserves this team slot.
        int activated=db.update("INSERT INTO company_memberships(company_id, user_id, status, joined_at) " +
                "VALUES (?, ?, 'ACTIVE', now()) ON CONFLICT (company_id, user_id) " +
                "DO UPDATE SET status = 'ACTIVE', joined_at = COALESCE(company_memberships.joined_at,now()), removed_at = NULL, " +
                "membership_version=company_memberships.membership_version+1 WHERE company_memberships.status<>'REMOVED'", companyId, actorId);
        if(activated==0)throw new AccessDeniedException("A Company Admin must reactivate your membership before you can accept a role invitation");
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
        if (!access.hasProjectRole(projectId, moderatorId, "USER"))
            throw new IllegalArgumentException("Assign this employee to the project before granting approval access");
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
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN"))throw new AccessDeniedException("Company role management permission required");
        access.lockCompanyAdministration(companyId);
        if(projectId==null || "PROJECT_ADMIN".equals(role))requireCompanyAdmin(companyId,actorId);
        else if(!access.hasCompanyRole(companyId,actorId,"COMPANY_ADMIN") && !access.mayManageProject(projectId,actorId))
            throw new AccessDeniedException("Project role management permission required");
        if (projectId == null && "COMPANY_ADMIN".equals(role)) {
            access.requireAnotherCompanyAdmin(companyId,targetId);
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
        int changed = projectId == null
                ? db.update("UPDATE role_assignments SET removed_at = now() WHERE user_id = ? AND role_key = ? " +
                        "AND company_id = ? AND project_id IS NULL AND removed_at IS NULL", targetId, role, companyId)
                : db.update("UPDATE role_assignments SET removed_at = now() WHERE user_id = ? AND role_key = ? " +
                        "AND company_id = ? AND project_id = ? AND removed_at IS NULL", targetId, role, companyId, projectId);
        if (changed == 0) throw new IllegalArgumentException("Active role assignment not found");
        if(projectId==null){
            db.update("UPDATE company_memberships SET membership_version=membership_version+1 WHERE company_id=? AND user_id=?",companyId,targetId);
            if("MODERATOR".equals(role))db.update("UPDATE moderator_grants SET revoked_at=now() WHERE company_id=? AND moderator_user_id=? AND revoked_at IS NULL",companyId,targetId);
        }
        audit.logAction(actorId, "ROLE_CHANGED", "Company", companyId, "Removed " + role + " from user " + targetId);
    }

    public void transferProjectOwnership(long companyId, long projectId, long newOwnerId, long actorId) {
        access.lockCompanyAdministration(companyId);
        requireCompanyAdmin(companyId, actorId);
        if (access.companyId(projectId) != companyId || !access.hasProjectRole(projectId, newOwnerId, "PROJECT_ADMIN"))
            throw new IllegalArgumentException("Choose an appointed Project Admin from this project");
        db.update("UPDATE projects SET owner_user_id=? WHERE id=? AND company_id=?", newOwnerId, projectId, companyId);
        audit.logAction(actorId, "ROLE_CHANGED", "Project", projectId, "Transferred project ownership to user " + newOwnerId);
    }

    public List<Map<String, Object>> projectRoles(long companyId, long projectId, long actorId) {
        if(access.hasPlatformRole(actorId,"PLATFORM_ADMIN"))throw new AccessDeniedException("Company project role view permission required");
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
        access.requireCompanyCapability(companyId,actorId,"canManageCompanyPeople");
    }

    private boolean validEmail(String email) {
        if (email == null || email.length() > 255 || email.chars().anyMatch(Character::isWhitespace)) return false;
        int at = email.indexOf('@');
        if (at < 1 || at != email.lastIndexOf('@')) return false;
        int dot = email.indexOf('.', at + 2);
        return dot > at + 1 && dot < email.length() - 1;
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
