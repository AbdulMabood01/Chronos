package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.*;
import com.maxwell.chronos.service.FeedbackReviewService;
import com.maxwell.chronos.web.FeedbackReviewController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedbackReviewController.class)
@Import({SecurityConfig.class, FeedbackReviewService.class})
class FeedbackReviewAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean ProjectRepository projects;
    @MockitoBean JdbcTemplate db;
    @MockitoBean com.maxwell.chronos.service.EmailAlertService emailAlerts;
    User user;
    String id = UUID.randomUUID().toString();
    String review = "{\"employeeId\":8,\"year\":2026,\"quarter\":3,\"summary\":\"Good work\",\"version\":0}";
    @BeforeEach void setup() {
        user = User.builder().id(7L).email("employee@example.com").role(UserRole.EMPLOYEE).isActive(true).entraId("subject").passwordHash("hash").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("subject").claim("preferred_username",user.getEmail()).claim("role","ADMIN"));
    }
    @Test void employeeAndManagerCannotManageReviewsOrReadOtherEmployeesDespiteForgedClaim() throws Exception {
        for (UserRole role : List.of(UserRole.EMPLOYEE, UserRole.PROJECT_ADMIN)) {
            user.setRole(role);
            mvc.perform(post("/feedback-reviews/reviews").with(token()).contentType("application/json").content(review)).andExpect(status().isForbidden());
            mvc.perform(put("/feedback-reviews/reviews/"+id).with(token()).contentType("application/json").content(review)).andExpect(status().isForbidden());
            mvc.perform(post("/feedback-reviews/reviews/"+id+"/publish").param("version","0").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/feedback-reviews/reviews/"+id+"/audit").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/feedback-reviews/reviews").param("employeeId","8").with(token())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(db);
    }
    @Test void unauthorizedAndInactiveUsersAreDenied() throws Exception {
        mvc.perform(get("/feedback-reviews/feedback")).andExpect(status().isUnauthorized());
        user.setIsActive(false);
        mvc.perform(get("/feedback-reviews/feedback").with(token())).andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }
    @Test void ownReviewsArePublishedOnlyAndNotCached() throws Exception {
        for (UserRole role : List.of(UserRole.EMPLOYEE, UserRole.PROJECT_ADMIN)) {
            user.setRole(role);
            mvc.perform(get("/feedback-reviews/reviews").with(token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
            mvc.perform(get("/feedback-reviews/reviews").param("employeeId","7").with(token())).andExpect(status().isOk());
            mvc.perform(post("/feedback-reviews/reviews").with(token()).contentType("application/json").content(review.replace("\"employeeId\":8", "\"employeeId\":7"))).andExpect(status().isForbidden());
            mvc.perform(put("/feedback-reviews/reviews/"+id).with(token()).contentType("application/json").content(review.replace("\"employeeId\":8", "\"employeeId\":7"))).andExpect(status().isForbidden());
        }
        verify(db,times(4)).queryForList(contains("AND r.published_at IS NOT NULL"),eq(7L));
        verifyNoMoreInteractions(db);
        // Project assignments never grant access to formal performance reviews.
        verifyNoInteractions(projects);
    }
    @Test void systemAdminCanBrowseAllEmployeeReviews() throws Exception {
        user.setRole(UserRole.ADMIN);
        mvc.perform(get("/feedback-reviews/reviews").with(token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(db).queryForList(contains("u.email AS employee_email"));
    }
    @Test void feedbackCannotBeSentToSelfAndReviewFieldsAreValidated() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        mvc.perform(post("/feedback-reviews/feedback").with(token()).contentType("application/json").content("{\"employeeId\":7,\"content\":\"Test\"}")).andExpect(status().isBadRequest());
        user.setRole(UserRole.ADMIN);
        mvc.perform(post("/feedback-reviews/reviews").with(token()).contentType("application/json").content(review.replace("\"quarter\":3","\"quarter\":5"))).andExpect(status().isBadRequest());
        verifyNoInteractions(db);
    }
}
