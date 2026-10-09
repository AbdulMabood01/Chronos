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

@WebMvcTest(CompanyMembershipController.class)
@Import(SecurityConfig.class)
class CompanyMembershipControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyMembershipService memberships;
    @MockitoBean UserService users;
    @MockitoBean PasswordService passwords;
    @MockitoBean UserRepository repository;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){
        var actor=User.builder().id(5L).email("actor@example.com").entraId("subject").role(UserRole.EMPLOYEE).isActive(true).passwordHash("test-hash").build();
        when(users.findUserEntityByEmail(actor.getEmail())).thenReturn(actor);when(repository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(sessions.valid(nullable(String.class),eq(5L))).thenReturn(true);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(builder->builder.subject("subject").claim("preferred_username","actor@example.com").claim("employee_id",99999));}
    @Test void unauthenticatedMembershipChangesAreDenied()throws Exception{
        mvc.perform(put("/companies/12/members/7/status").contentType("application/json").content("{\"status\":\"REMOVED\",\"version\":0}")).andExpect(status().isUnauthorized());verifyNoInteractions(memberships);
    }
    @Test void mutationsUseAuthenticatedActorAndPathCompany()throws Exception{
        var input=new CompanyMembershipService.StatusInput("REMOVED",0L);when(memberships.changeStatus(12,7,5,input)).thenReturn(new CompanyMembershipService.Result(7,"REMOVED",1));
        mvc.perform(put("/companies/12/members/7/status").with(token()).contentType("application/json").content("{\"status\":\"REMOVED\",\"version\":0,\"actorId\":99999,\"companyId\":13}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REMOVED")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(memberships).changeStatus(12,7,5,input);
    }
    @Test void globalRolePromotionIsRejectedByRequestValidation()throws Exception{
        mvc.perform(post("/companies/12/members/7/roles").with(token()).contentType("application/json").content("{\"role\":\"PLATFORM_ADMIN\",\"version\":0}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(memberships);
    }
    @Test void missingMutationVersionIsRejected()throws Exception{
        mvc.perform(put("/companies/12/members/7/status").with(token()).contentType("application/json").content("{\"status\":\"REMOVED\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(memberships);
    }
    @Test void recoveryUsesRegisteredAddressAndIgnoresCallerSuppliedEmail()throws Exception{
        when(memberships.recoveryAddress(12,7,5)).thenReturn("registered@example.com");
        mvc.perform(post("/companies/12/members/7/password-reset").with(token()).contentType("application/json").content("{\"email\":\"attacker@example.com\"}"))
            .andExpect(status().isOk());verify(passwords).forgot("registered@example.com");verify(passwords,never()).forgot("attacker@example.com");
    }
}
