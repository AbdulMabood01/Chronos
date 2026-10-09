package com.maxwell.chronos.service;

import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest", matches="true")
class CompanyContextLocalDbTest {
    @Test void realMembershipChangesAndUnauthorizedSelectionsAreEnforced() {
        var datasource = new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev", "chronos_user", "chronos_password");
        var db = new JdbcTemplate(datasource);
        var service = new CompanyManagementService(db, new CompanyAccessService(db), mock(UserRepository.class),
                mock(InvitationDeliveryService.class), mock(AuditService.class), mock(OnboardingService.class), mock(PasswordEncoder.class));
        new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status -> {
            status.setRollbackOnly();
            String key = "context-" + UUID.randomUUID();
            long user = db.queryForObject("INSERT INTO users(employee_id, first_name, last_name, email) VALUES (?, 'Context', 'Test', ?) RETURNING id", Long.class, key, key+"@example.com");
            long a = db.queryForObject("INSERT INTO companies(name, slug) VALUES ('Context A', ?) RETURNING id", Long.class, key+"-a");
            long b = db.queryForObject("INSERT INTO companies(name, slug) VALUES ('Context B', ?) RETURNING id", Long.class, key+"-b");
            assertTrue(service.companyContext(user).memberships().isEmpty());
            assertThrows(AccessDeniedException.class, () -> service.validateCompanyContext(a, user));
            db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'PENDING')", a, user);
            assertThrows(AccessDeniedException.class, () -> service.validateCompanyContext(a, user));
            db.update("UPDATE company_memberships SET status='ACTIVE' WHERE company_id=? AND user_id=?", a, user);
            assertEquals(a, ((Number)service.validateCompanyContext(a,user).get("id")).longValue());
            assertEquals(1, service.companyContext(user).memberships().size());
            assertThrows(AccessDeniedException.class, () -> service.validateCompanyContext(b,user));
            db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?", a,user);
            assertThrows(AccessDeniedException.class, () -> service.validateCompanyContext(a,user));
            assertTrue(service.companyContext(user).memberships().isEmpty());
        });
    }
}
