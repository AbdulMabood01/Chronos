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

    @Override
    public void run(ApplicationArguments args) {
        reset();
    }

    @Transactional
    public Map<String, Object> reset() {
        String tables = jdbc.queryForObject("""
                select string_agg(format('%I.%I', schemaname, tablename), ', ')
                from pg_tables
                where schemaname = 'public'
                  and tablename not in ('flyway_schema_history', 'vacation_types', 'system_settings')
                """, String.class);
        if (tables != null && !tables.isBlank()) jdbc.execute("TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE");
        entityManager.clear();

        User admin = saveUser("E2E-ADMIN", "Super", "Admin", ADMIN_EMAIL, UserRole.ADMIN);
        User projectAdmin = saveUser("E2E-PROJECT-ADMIN", "Project", "Admin", PROJECT_ADMIN_EMAIL, UserRole.PROJECT_ADMIN);
        User employee = saveUser("E2E-EMPLOYEE", "Test", "Employee", EMPLOYEE_EMAIL, UserRole.EMPLOYEE);

        Project project = projects.saveAndFlush(Project.builder()
                .code("E2E-CORE").name("E2E Core Project").description("Deterministic Playwright fixture")
                .isActive(true).status(ProjectStatus.ACTIVE).totalAllocatedHours(new BigDecimal("500"))
                .projectManager(projectAdmin).projectManagerHoursApprover(projectAdmin).build());
        assignments.saveAndFlush(ProjectAssignment.builder().project(project).user(employee).assignedBy(projectAdmin)
                .isActive(true).startDate(LocalDate.now().minusMonths(2)).endDate(LocalDate.now().plusMonths(2))
                .billRate(new BigDecimal("100")).plannedHours(new BigDecimal("160")).build());

        return Map.of(
                "adminId", admin.getId(), "projectAdminId", projectAdmin.getId(),
                "employeeId", employee.getId(), "projectId", project.getId());
    }

    private User saveUser(String employeeId, String firstName, String lastName, String email, UserRole role) {
        return users.saveAndFlush(User.builder()
                .employeeId(employeeId).firstName(firstName).lastName(lastName).email(email)
                .entraId("e2e:" + employeeId.toLowerCase()).passwordHash(passwords.encode(PASSWORD))
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
