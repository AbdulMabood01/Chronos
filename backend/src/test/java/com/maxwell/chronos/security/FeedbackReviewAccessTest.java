package com.maxwell.chronos.security;
import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.web.FeedbackReviewController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Global routes are retired; company scope, grants and privacy are covered by CompanyCommunications tests. */
@WebMvcTest(FeedbackReviewController.class) @Import(SecurityConfig.class)
class FeedbackReviewAccessTest {
 @Autowired MockMvc mvc;
 @MockitoBean JwtDecoder decoder;
 @MockitoBean com.maxwell.chronos.service.AuthSessionService sessions;
 @MockitoBean UserRepository users;
 User actor;
 @BeforeEach void setup(){actor=User.builder().id(7L).email("member@example.com").isActive(true).entraId("subject").passwordHash("hash").role(UserRole.EMPLOYEE).build();when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));when(sessions.valid(nullable(String.class),anyLong())).thenReturn(true);}
 @Test void allGlobalRolesAndForgedClaimsReceiveGoneForReadsAndMutations() throws Exception {
  for(var role:UserRole.values()){
   actor.setRole(role);
   for(String suffix:List.of("/feedback","/employees","/reviews","/abc/audit"))mvc.perform(get("/feedback-reviews"+suffix).with(jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail()).claim("role","ADMIN")))).andExpect(status().isGone());
   mvc.perform(post("/feedback-reviews").with(jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail()))).contentType("application/json").content("{}")).andExpect(status().isGone());
   mvc.perform(patch("/feedback-reviews/abc").with(jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail()))).contentType("application/json").content("{}")).andExpect(status().isGone());
   mvc.perform(delete("/feedback-reviews/abc").with(jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail())))).andExpect(status().isGone());
  }
 }
 @Test void authenticationAndActiveAccountAreStillRequired() throws Exception {
  mvc.perform(get("/feedback-reviews")).andExpect(status().isUnauthorized());actor.setIsActive(false);
  mvc.perform(get("/feedback-reviews").with(jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail())))).andExpect(status().isUnauthorized());
 }
}
