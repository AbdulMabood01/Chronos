package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.PasswordService;
import com.maxwell.chronos.web.PasswordController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordController.class)
@Import(SecurityConfig.class)
class PasswordAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean PasswordService passwords;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;
    User user;
    String change = "{\"currentPassword\":\"OldPassword123\",\"newPassword\":\"NewPassword123\",\"confirmation\":\"NewPassword123\"}";
    @BeforeEach void setup() {
        user = User.builder().id(1L).email("alice@example.com").entraId("subject").isActive(true).passwordHash("hash").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    @Test void recoveryIsPublicAndChangeRequiresAuthentication() throws Exception {
        mvc.perform(post("/auth/forgot-password").contentType("application/json").content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("If an active account")));
        mvc.perform(post("/auth/reset-password/validate").contentType("application/json").content("{\"token\":\"" + "a".repeat(43) + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/reset-password").contentType("application/json").content(change.replace("\"currentPassword\":\"OldPassword123\"", "\"token\":\"" + "a".repeat(43) + "\"")))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/change-password").contentType("application/json").content(change)).andExpect(status().isUnauthorized());
        verify(passwords, never()).change(any(), any(), any(), any());
    }
    @Test void changeUsesAuthenticatedSubject() throws Exception {
        mvc.perform(post("/auth/change-password").with(jwt().jwt(j -> j.subject("subject").claim("preferred_username", user.getEmail())))
                .contentType("application/json").content(change)).andExpect(status().isOk());
        verify(passwords).change("subject", "OldPassword123", "NewPassword123", "NewPassword123");
    }
    @Test void oldSessionsAreRejectedAndNewSessionsWorkAfterPasswordChange() throws Exception {
        user.setCredentialVersion(1);
        for (Long version : new Long[]{null, 0L}) {
            mvc.perform(post("/auth/change-password").with(jwt().jwt(j -> {
                j.subject("subject").claim("preferred_username", user.getEmail());
                if (version != null) j.claim("credential_version", version);
            })).contentType("application/json").content(change)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(passwords);
        mvc.perform(post("/auth/change-password").with(jwt().jwt(j -> j.subject("subject")
                .claim("preferred_username", user.getEmail()).claim("credential_version", 1L)))
                .contentType("application/json").content(change)).andExpect(status().isOk());
    }
    @Test void malformedEmailAndMissingConfirmationAreRejected() throws Exception {
        mvc.perform(post("/auth/forgot-password").contentType("application/json").content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/reset-password").contentType("application/json").content("{\"token\":\"abc\",\"newPassword\":\"NewPassword123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwords);
    }
}
