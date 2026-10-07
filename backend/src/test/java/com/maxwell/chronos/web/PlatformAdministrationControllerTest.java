package com.maxwell.chronos.web;
import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(PlatformAdministrationController.class) @Import(SecurityConfig.class)
class PlatformAdministrationControllerTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean AuthSessionService sessions;@MockitoBean UserRepository repository;@MockitoBean UserService users;@MockitoBean PlatformAdministrationService platform;@MockitoBean CompanyManagementService companies;
 User actor;
 @BeforeEach void setup(){actor=User.builder().id(7L).email("operator@example.com").role(UserRole.EMPLOYEE).isActive(true).entraId("subject").passwordHash("hash").build();when(repository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));when(users.findUserEntityByEmail(actor.getEmail())).thenReturn(actor);when(sessions.valid(nullable(String.class),anyLong())).thenReturn(true);}
 org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail()).claim("role","ADMIN"));}
 @Test void pathCompanyAndActorAreUsedInsteadOfForgedBodyFields() throws Exception {mvc.perform(put("/platform/companies/12/plan").with(token()).contentType("application/json").content("{\"tier\":\"MULTIPLE\",\"projectLimit\":10,\"teamLimit\":20,\"version\":3,\"reason\":\"Growth\",\"companyId\":13,\"actorId\":99}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(platform).plan(eq(12L),eq(actor.getEmail()),argThat(in->in.version()==3L&&in.projectLimit()==10));}
 @Test void requiresRevisionsReasonsAndSupportedActions() throws Exception {mvc.perform(put("/platform/companies/12/status").with(token()).contentType("application/json").content("{\"suspended\":true,\"reason\":\"Suspension\"}")).andExpect(status().isBadRequest());mvc.perform(post("/platform/accounts/8/actions").with(token()).contentType("application/json").content("{\"action\":\"COMPANY_ADMIN\",\"version\":0,\"reason\":\"Role\"}")).andExpect(status().isBadRequest());mvc.perform(put("/platform/companies/12/plan").with(token()).contentType("application/json").content("{\"tier\":\"CUSTOM\",\"projectLimit\":0,\"teamLimit\":6,\"version\":0,\"reason\":\"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(platform);}
 @Test void forgedGlobalAdminClaimsDoNotBypassScopedServiceDenial() throws Exception {when(platform.accounts(actor.getEmail(),null)).thenThrow(new AccessDeniedException("Platform Admin permission required"));mvc.perform(get("/platform/accounts").with(token())).andExpect(status().isForbidden());mvc.perform(get("/platform/audit")).andExpect(status().isUnauthorized());}
 @Test void accountMutationUsesServerIdentityAndExpectedRevision() throws Exception {mvc.perform(post("/platform/accounts/8/actions").with(token()).contentType("application/json").content("{\"action\":\"LOCK\",\"version\":2,\"reason\":\"Security review\"}")).andExpect(status().isOk());verify(platform).account(eq(8L),eq(actor.getEmail()),argThat(in->in.action().equals("LOCK")&&in.version()==2));}
 @Test void adminInvitationManagementIsASeparateProvisioningPath() throws Exception {mvc.perform(post("/platform/companies/12/admin-invitations/4/resend").with(token())).andExpect(status().isOk());verify(platform).company(12,actor.getEmail());verify(companies).resendInvitation(12,4,7);}
}
