package com.maxwell.chronos.web;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyBillingController.class) @Import(SecurityConfig.class)
class CompanyBillingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyBillingService billing;
    @MockitoBean UserRepository users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){var u=User.builder().id(5L).entraId("billing-user").email("billing@example.com").isActive(true).passwordHash("test-hash").build();when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));when(sessions.valid(nullable(String.class),eq(5L))).thenReturn(true);}
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(b->b.subject("billing-user").claim("preferred_username","billing@example.com"));}
    @Test void onlyCatalogAndSignatureVerifiedWebhookAreAnonymous()throws Exception{
        mvc.perform(get("/billing/catalog")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/companies/12/billing")).andExpect(status().isUnauthorized());
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid signature")).when(billing).webhook(any(),nullable(String.class));
        mvc.perform(post("/billing/stripe/webhook").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void serverActorPathAndSelectionAreUsedInsteadOfForgedAmounts()throws Exception{
        mvc.perform(post("/companies/12/billing/quotes").with(token()).contentType("application/json").content("{\"plan\":\"PRO\",\"months\":12,\"extraSeats\":5,\"kind\":\"PLAN\",\"amount_cents\":1,\"company\":99,\"actor\":99}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(billing).quote(12,"billing@example.com",new CompanyBillingService.Selection("PRO",12,5,"PLAN"));
    }
    @Test void companyAndReceiptForgeryReachScopedAuthorization()throws Exception{
        when(billing.summary(13,"billing@example.com")).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));mvc.perform(get("/companies/13/billing").with(token())).andExpect(status().isForbidden());
        UUID id=UUID.randomUUID();when(billing.receipt(13,"billing@example.com",id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));mvc.perform(get("/companies/13/billing/receipts/"+id).with(token())).andExpect(status().isForbidden());
    }
}
