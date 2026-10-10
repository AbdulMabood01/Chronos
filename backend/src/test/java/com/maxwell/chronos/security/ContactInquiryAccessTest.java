package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.ContactInquiryService;
import com.maxwell.chronos.service.AuthSessionService;
import com.maxwell.chronos.web.ContactInquiryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContactInquiryController.class)
@Import(SecurityConfig.class)
class ContactInquiryAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContactInquiryService inquiries;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AuthSessionService sessions;
    private final String valid = "{\"name\":\"Jordan\",\"company\":\"Acme\",\"email\":\"jordan@example.com\",\"message\":\"Discuss our team\",\"source\":\"LANDING\"}";
    @Test void readinessIsPublicWithoutRevealingInbox() throws Exception {
        mvc.perform(get("/contact/status")).andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.recipient").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void validInquiryIsPublicButOtherMethodsStayProtected() throws Exception {
        mvc.perform(post("/contact/inquiries").contentType("application/json").content(valid)).andExpect(status().isOk());
        verify(inquiries).send(any());
        mvc.perform(get("/contact/inquiries")).andExpect(status().isUnauthorized());
        mvc.perform(put("/contact/status")).andExpect(status().isUnauthorized());
    }
    @Test void invalidEmailBlankNameAndOversizedMessageAreRejectedBeforeSending() throws Exception {
        for (String body : new String[]{valid.replace("jordan@example.com", "invalid"), valid.replace("Jordan", " "), valid.replace("Discuss our team", "x".repeat(3001)), valid.replace("LANDING", "OTHER")})
            mvc.perform(post("/contact/inquiries").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(inquiries, never()).send(any());
    }
    @Test void mailUnavailableIsNotReportedAsSuccessfulSubmission() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Contact inquiries are not available yet"))
            .when(inquiries).send(any());
        mvc.perform(post("/contact/inquiries").contentType("application/json").content(valid)).andExpect(status().isServiceUnavailable());
    }
}
