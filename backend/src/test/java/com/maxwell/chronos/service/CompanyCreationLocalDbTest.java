package com.maxwell.chronos.service;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyCreationLocalDbTest {
 final DriverManagerDataSource source=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");final JdbcTemplate db=new JdbcTemplate(source);
 @Test void createsCompanyAndOnlyAPendingCompanyAdminInvitationAndQueuesDelivery(){new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status->{status.setRollbackOnly();long actor=db.queryForObject("SELECT user_id FROM role_assignments WHERE role_key='PLATFORM_ADMIN' AND company_id IS NULL AND removed_at IS NULL LIMIT 1",Long.class);var delivery=mock(InvitationDeliveryService.class);var access=new CompanyAccessService(db);var service=new CompanyManagementService(db,access,mock(UserRepository.class),delivery,mock(AuditService.class),mock(OnboardingService.class),mock(PasswordEncoder.class));String slug="flow-test-"+UUID.randomUUID();long id=service.createCompany("Test Company",slug,"admin@example.com",actor);assertEquals(1,db.queryForObject("SELECT count(*) FROM company_invitations WHERE company_id=? AND role_key='COMPANY_ADMIN' AND accepted_at IS NULL",Integer.class,id));assertEquals(0,db.queryForObject("SELECT count(*) FROM company_memberships WHERE company_id=?",Integer.class,id));assertEquals(0,db.queryForObject("SELECT count(*) FROM role_assignments WHERE company_id=?",Integer.class,id));verify(delivery).company(anyLong(),anyString());assertEquals(409,assertThrows(ResponseStatusException.class,()->service.createCompany("Duplicate",slug,"other@example.com",actor)).getStatusCode().value());});}
}
