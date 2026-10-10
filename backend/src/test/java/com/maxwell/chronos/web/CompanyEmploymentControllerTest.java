package com.maxwell.chronos.web;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.CompanyEmploymentDTO;
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

@WebMvcTest(CompanyEmploymentController.class)
@Import(SecurityConfig.class)
class CompanyEmploymentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyEmploymentService employment;
    @MockitoBean CompanyMemberProfileService profiles;
    @MockitoBean MemberDetailCorrectionService corrections;
    @MockitoBean UserService users;
    @MockitoBean UserRepository repository;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){
        var actor=User.builder().id(5L).email("actor@example.com").entraId("actor-subject").role(UserRole.EMPLOYEE)
                .isActive(true).passwordHash("test-hash").build();
        when(users.findUserEntityByEmail(actor.getEmail())).thenReturn(actor);
        when(repository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(sessions.valid(nullable(String.class),eq(5L))).thenReturn(true);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(builder->builder.subject("actor-subject")
            .claim("preferred_username","actor@example.com").claim("employee_id",99999));}
    @Test void unauthenticatedRequestsAreDenied()throws Exception{mvc.perform(get("/companies/12/members/7/employment")).andExpect(status().isUnauthorized());verifyNoInteractions(employment);}
    @Test void actorComesFromAuthenticatedIdentityAndResponseContainsOnlyEmployment()throws Exception{
        when(employment.get(12,7,5)).thenReturn(new CompanyEmploymentDTO(12,7,"A-001","Engineer",null,"ACTIVE",0));
        mvc.perform(get("/companies/12/members/7/employment").with(token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("A-001")).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.dateOfBirth").doesNotExist());
        verify(employment).get(12,7,5);
    }
    @Test void malformedEditVersionIsRejectedBeforeService()throws Exception{
        mvc.perform(put("/companies/12/members/7/employment").with(token()).contentType("application/json")
                .content("{\"employeeId\":\"A-001\",\"version\":-1}")).andExpect(status().isBadRequest());verifyNoInteractions(employment);
    }
    @Test void missingVersionIsRejectedBeforeService()throws Exception{
        mvc.perform(put("/companies/12/members/7/employment").with(token()).contentType("application/json")
                .content("{\"jobTitle\":\"Engineer\"}")).andExpect(status().isBadRequest());verifyNoInteractions(employment);
    }
}
