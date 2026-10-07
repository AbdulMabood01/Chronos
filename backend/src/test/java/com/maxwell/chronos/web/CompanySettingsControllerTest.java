package com.maxwell.chronos.web;
import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CompanySettingsController.class,SettingsController.class})
@Import(SecurityConfig.class)
class CompanySettingsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanySettingsService settings;
    @MockitoBean PlatformSettingsService platform;
    @MockitoBean UserService users;
    @MockitoBean UserRepository repository;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){
        var actor=User.builder().id(5L).email("actor@example.com").entraId("actor-subject").role(UserRole.EMPLOYEE).isActive(true).passwordHash("test-hash").build();
        when(users.findUserEntityByEmail(actor.getEmail())).thenReturn(actor);when(repository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(sessions.valid(nullable(String.class),eq(5L))).thenReturn(true);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(builder->builder.subject("actor-subject").claim("preferred_username","actor@example.com").claim("employee_id",99999));}
    @Test void unauthenticatedSettingsDenied()throws Exception{mvc.perform(get("/companies/12/settings")).andExpect(status().isUnauthorized());mvc.perform(get("/platform/settings")).andExpect(status().isUnauthorized());verifyNoInteractions(settings,platform);}
    @Test void updateUsesAuthenticatedActorAndPathCompanyIgnoringForgedScope()throws Exception{
        mvc.perform(put("/companies/12/settings/sick_days_per_year").with(token()).contentType("application/json").content("{\"value\":\"8.5\",\"version\":2,\"companyId\":13,\"actorId\":999}")) .andExpect(status().isOk());
        verify(settings).update(12,5,"sick_days_per_year",new CompanySettingsService.Input("8.5",2L));
    }
    @Test void requiredRevisionAndValueValidatedBeforeService()throws Exception{
        for(String body:new String[]{"{\"value\":\"8\"}","{\"value\":\"8\",\"version\":-1}","{\"version\":1}"})mvc.perform(put("/companies/12/settings/sick_days_per_year").with(token()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(settings);
    }
    @Test void companyPermissionDenialIs403()throws Exception{
        when(settings.get(12,5)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
        mvc.perform(get("/companies/12/settings").with(token())).andExpect(status().isForbidden());
    }
    @Test void platformUpdateUsesServerIdentity()throws Exception{
        mvc.perform(put("/platform/settings/platform.timesheet.reminders.enabled").with(token()).contentType("application/json").content("{\"value\":\"false\",\"version\":3}")) .andExpect(status().isOk());
        verify(platform).update(5,PlatformSettingsService.REMINDERS,new CompanySettingsService.Input("false",3L));
    }
    @Test void legacyGlobalPolicyAndBulkEndpointsAreGone()throws Exception{
        mvc.perform(get("/settings").with(token())).andExpect(status().isGone());
        mvc.perform(put("/settings/company_name").with(token()).contentType("application/json").content("{\"value\":\"Global\"}")) .andExpect(status().isGone());
        mvc.perform(post("/settings/leave-defaults/apply").with(token()).contentType("application/json").content("{}")) .andExpect(status().isGone());verifyNoInteractions(settings,platform);
    }
}
