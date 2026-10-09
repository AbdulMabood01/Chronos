package com.maxwell.chronos.e2e;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.domain.ProjectAssignment;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.domain.EmployeeInvitation;
import com.maxwell.chronos.enums.ProjectStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.EmployeeInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Component
@Profile("e2e")
@RequiredArgsConstructor
public class E2eFixtures implements ApplicationRunner {
    public static final String PASSWORD = "Chronos-E2E-123!";
    public static final String ADMIN_EMAIL = "admin@e2e.chronos.test";
    public static final String PROJECT_ADMIN_EMAIL = "project-admin@e2e.chronos.test";
    public static final String EMPLOYEE_EMAIL = "employee@e2e.chronos.test";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;
    private final EmployeeInvitationRepository invitations;
    private String fixturePasswordHash;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        reset();
    }

    @Transactional
    public Map<String, Object> reset() {
        String database = jdbc.queryForObject("select current_database()", String.class);
        if (database == null || !database.endsWith("_e2e"))
            throw new IllegalStateException("E2E reset requires a dedicated database ending in _e2e");
        String tables = jdbc.queryForObject("""
                select string_agg(format('%I.%I', schemaname, tablename), ', ')
                from pg_tables
                where schemaname = 'public'
                  and tablename not in ('flyway_schema_history', 'vacation_types', 'system_settings', 'role_definitions')
                """, String.class);
        if (tables != null && !tables.isBlank()) jdbc.execute("TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE");
        entityManager.clear();

        User admin = saveUser("E2E-ADMIN", "Super", "Admin", ADMIN_EMAIL, UserRole.ADMIN);
        User projectAdmin = saveUser("E2E-PROJECT-ADMIN", "Project", "Admin", PROJECT_ADMIN_EMAIL, UserRole.PROJECT_ADMIN);
        User employee = saveUser("E2E-EMPLOYEE", "Test", "Employee", EMPLOYEE_EMAIL, UserRole.EMPLOYEE);
        User manager = saveUser("E2E-MANAGER", "Project", "Manager", "manager@e2e.chronos.test", UserRole.EMPLOYEE);
        User platform = saveUser("E2E-PLATFORM", "Platform", "Admin", "platform@e2e.chronos.test", UserRole.ADMIN);
        User otherAdmin = saveUser("E2E-OTHER", "Other", "Admin", "other-admin@e2e.chronos.test", UserRole.EMPLOYEE);
        User secondAdmin = saveUser("E2E-SECOND", "Second", "Admin", "second-admin@e2e.chronos.test", UserRole.EMPLOYEE);
        User creator = saveUser("E2E-CREATOR", "Company Project", "Admin", "creator@e2e.chronos.test", UserRole.EMPLOYEE);
        User moderator = saveUser("E2E-MODERATOR", "Time", "Moderator", "moderator@e2e.chronos.test", UserRole.EMPLOYEE);
        long company = company("E2E Company", "e2e-company");
        long otherCompany = company("Other Company", "other-company");
        for (User user : java.util.List.of(admin, projectAdmin, employee, manager, secondAdmin, creator, moderator)) member(company, user);
        member(otherCompany, otherAdmin);
        member(otherCompany, employee);
        role(admin,"COMPANY_ADMIN",company,null);
        role(secondAdmin,"COMPANY_ADMIN",company,null);
        role(otherAdmin,"COMPANY_ADMIN",otherCompany,null);
        role(creator,"PROJECT_ADMIN",company,null);
        role(platform,"PLATFORM_ADMIN",null,null);

        Project project = projects.saveAndFlush(Project.builder()
                .companyId(company).ownerUserId(projectAdmin.getId())
                .code("E2E-CORE").name("E2E Core Project").description("Deterministic Playwright fixture")
                .isActive(true).status(ProjectStatus.ACTIVE).totalAllocatedHours(new BigDecimal("500"))
                .projectManager(manager).projectManagerHoursApprover(projectAdmin).build());
        for (User user : java.util.List.of(projectAdmin,employee,manager,moderator)) {
            jdbc.update("INSERT INTO project_memberships(project_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now())",project.getId(),user.getId());
            role(user,user == projectAdmin ? "PROJECT_ADMIN" : user == manager ? "PROJECT_MANAGER" : "USER",company,project.getId());
        }
        assignments.saveAndFlush(ProjectAssignment.builder().project(project).user(employee).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().plusYears(2))
                .billRate(new BigDecimal("100")).plannedHours(new BigDecimal("160")).build());
        assignments.saveAndFlush(ProjectAssignment.builder().project(project).user(moderator).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().plusYears(2))
                .billRate(BigDecimal.ZERO).plannedHours(BigDecimal.ZERO).build());
        assignments.saveAndFlush(ProjectAssignment.builder().project(project).user(manager).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().plusYears(2))
                .billRate(new BigDecimal("100")).plannedHours(new BigDecimal("160")).build());
        jdbc.update("INSERT INTO moderator_grants(company_id,project_id,moderator_user_id,timesheets,expenses,starts_on,ends_on,granted_by_user_id) VALUES (?,?,?,true,false,?,?,?)",
                company,project.getId(),moderator.getId(),LocalDate.now().minusYears(2),LocalDate.now().plusYears(2),admin.getId());
        Project daily = projects.saveAndFlush(Project.builder().companyId(company).ownerUserId(projectAdmin.getId())
                .code("E2E-DAILY").name("E2E Daily Project").isActive(true).status(ProjectStatus.ACTIVE)
                .approvalFrequency("DAILY").projectManager(manager).projectManagerHoursApprover(projectAdmin).build());
        for (User user : java.util.List.of(projectAdmin,employee,manager)) {
            jdbc.update("INSERT INTO project_memberships(project_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now())",daily.getId(),user.getId());
            role(user,user == projectAdmin ? "PROJECT_ADMIN" : user == manager ? "PROJECT_MANAGER" : "USER",company,daily.getId());
            if(user != projectAdmin) assignments.saveAndFlush(ProjectAssignment.builder().project(daily).user(user).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().plusYears(2))
                .billRate(new BigDecimal("100")).plannedHours(new BigDecimal("160")).build());
        }

        Project weekly = projects.saveAndFlush(Project.builder().companyId(company).ownerUserId(projectAdmin.getId())
                .code("E2E-WEEKLY").name("E2E Weekly Project").isActive(true).status(ProjectStatus.ACTIVE)
                .approvalFrequency("WEEKLY").projectManager(manager).projectManagerHoursApprover(projectAdmin).build());
        for (User user : java.util.List.of(projectAdmin,employee,manager)) {
            jdbc.update("INSERT INTO project_memberships(project_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now())",weekly.getId(),user.getId());
            role(user,user == projectAdmin ? "PROJECT_ADMIN" : user == manager ? "PROJECT_MANAGER" : "USER",company,weekly.getId());
            if(user != projectAdmin) assignments.saveAndFlush(ProjectAssignment.builder().project(weekly).user(user).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().plusYears(2))
                .billRate(new BigDecimal("100")).plannedHours(new BigDecimal("160")).build());
        }
        return Map.of(
                "adminId", admin.getId(), "projectAdminId", projectAdmin.getId(),
                "employeeId", employee.getId(), "projectId", project.getId(),"managerId",manager.getId(),
                "companyId",company,"otherCompanyId",otherCompany,"today",LocalDate.now().toString(),"dailyProjectId",daily.getId(),"weeklyProjectId",weekly.getId());
    }

    private long company(String name,String slug) {
        long id=jdbc.queryForObject("INSERT INTO companies(name,slug,plan_tier,project_limit,team_limit) VALUES (?,?,'CUSTOM',20,50) RETURNING id",Long.class,name,slug);
        jdbc.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version,reason) VALUES (?,?,'CUSTOM','CONTRACT',now(),now()+interval '1 day',20,50,'e2e-fixture','Explicit isolated workflow-test capacity; no payment')",java.util.UUID.randomUUID(),id);
        return id;
    }
    private void member(long company,User user) {
        jdbc.update("INSERT INTO company_memberships(company_id,user_id,status,joined_at,employee_id,job_title,joining_date) VALUES (?,?,'ACTIVE',now(),?,'Software Engineer',?)",company,user.getId(),user.getEmployeeId(),LocalDate.now().minusYears(2));
        for(int year=LocalDate.now().getYear();year<=LocalDate.now().getYear()+1;year++)
            jdbc.update("INSERT INTO company_leave_allowances(company_id,user_id,leave_year,vacation_days,sick_days,bereavement_days) VALUES (?,?,?,20,10,5)",company,user.getId(),year);
    }
    private void role(User user,String role,Long company,Long project) {
        jdbc.update("INSERT INTO role_assignments(user_id,role_key,company_id,project_id) VALUES (?,?,?,?)",user.getId(),role,company,project);
    }
    @Transactional
    public String issueCompanyInvitationToken(long invitationId) {
        String token="CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
        if(jdbc.update("UPDATE company_invitations SET token_hash=? WHERE id=? AND accepted_at IS NULL AND revoked_at IS NULL",sha256(token),invitationId)!=1)
            throw new IllegalArgumentException("Pending invitation not found");
        return token;
    }

    @Transactional
    public void expireCompanyInvitation(long invitationId) {
        if (jdbc.update("UPDATE company_invitations SET created_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE id=? AND accepted_at IS NULL AND revoked_at IS NULL", invitationId) != 1)
            throw new IllegalArgumentException("Pending invitation not found");
    }

    private User saveUser(String employeeId, String firstName, String lastName, String email, UserRole role) {
        if (fixturePasswordHash == null) fixturePasswordHash = passwords.encode(PASSWORD);
        return users.saveAndFlush(User.builder()
                .employeeId(employeeId).firstName(firstName).lastName(lastName).email(email)
                .entraId("e2e:" + employeeId.toLowerCase()).passwordHash(fixturePasswordHash)
                .role(role).isActive(true).profileCompleted(true).timezone("America/Chicago")
                .joiningDate(LocalDate.now().minusYears(2)).build());
    }

    @Transactional
    public String issueInvitationToken(long employeeId) {
        String token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        invitations.findByEmployeeId(employeeId).ifPresent(invitations::delete);
        EmployeeInvitation invitation = new EmployeeInvitation();
        invitation.setEmployeeId(employeeId);
        invitation.setTokenHash(sha256(token));
        invitation.setCreatedAt(Instant.now());
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        invitations.saveAndFlush(invitation);
        return token;
    }

    @Transactional
    public String issuePasswordResetToken(long employeeId) {
        String token = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        User user = users.findById(employeeId).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        user.setPasswordResetHash(sha256(token));
        user.setPasswordResetRequestedAt(Instant.now());
        user.setPasswordResetExpiresAt(Instant.now().plusSeconds(1800));
        users.saveAndFlush(user);
        return token;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
