package com.maxwell.chronos.service;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.EmptyResultDataAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CompanyCreationTest {
 final JdbcTemplate db=mock(JdbcTemplate.class);final CompanyAccessService access=mock(CompanyAccessService.class);final InvitationDeliveryService delivery=mock(InvitationDeliveryService.class);
 final CompanyManagementService service=new CompanyManagementService(db,access,mock(UserRepository.class),delivery,mock(AuditService.class),mock(OnboardingService.class),mock(PasswordEncoder.class));
 @Test void onlyScopedPlatformAdminCanProvision(){assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.createCompany("Acme","acme","admin@acme.com",1));verifyNoInteractions(db,delivery);}
 @Test void validatesInputsBeforeCreation(){when(access.hasPlatformRole(1,"PLATFORM_ADMIN")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.createCompany("Acme","acme corp","admin@acme.com",1));assertThrows(IllegalArgumentException.class,()->service.createCompany("Acme","acme",null,1));verifyNoInteractions(db,delivery);}
 @Test void companyInitialInvitationAndDeliveryAreCreatedWithoutSendingSmtp(){
  when(access.hasPlatformRole(1,"PLATFORM_ADMIN")).thenReturn(true);when(db.queryForObject(startsWith("INSERT INTO companies"),eq(Long.class),eq("Acme"),eq("acme"))).thenReturn(12L);
  when(db.queryForObject(startsWith("INSERT INTO company_invitations"),eq(Long.class),eq(12L),isNull(),eq("admin@acme.com"),eq("COMPANY_ADMIN"),anyString(),eq(1L),any())).thenReturn(31L);
  assertEquals(12,service.createCompany(" Acme "," ACME "," Admin@Acme.com ",1));verify(delivery).company(eq(31L),argThat(token->token.matches("[A-Za-z0-9_-]{43}")));
 }
 @Test void duplicateWorkspaceIsConflict(){when(access.hasPlatformRole(1,"PLATFORM_ADMIN")).thenReturn(true);when(db.queryForObject(startsWith("INSERT INTO companies"),eq(Long.class),anyString(),anyString())).thenThrow(new EmptyResultDataAccessException(1));assertEquals(409,assertThrows(ResponseStatusException.class,()->service.createCompany("Acme","acme","admin@acme.com",1)).getStatusCode().value());verifyNoInteractions(delivery);}
}
