package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.AnnouncementService;
import com.maxwell.chronos.web.AnnouncementController;
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

@WebMvcTest(AnnouncementController.class)
@Import({SecurityConfig.class, AnnouncementService.class})
class AnnouncementAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean JdbcTemplate db;
    User user;
    String id=UUID.randomUUID().toString();
    String input="""
        {"title":"News","content":"Hello","publishDate":"2026-09-22","priority":"IMPORTANT","status":"DRAFT","acknowledgmentRequired":true,"version":0}
        """;
    @BeforeEach void setup() {
        user=User.builder().id(7L).email("employee@example.com").role(UserRole.EMPLOYEE).isActive(true).entraId("subject").passwordHash("hash").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("subject").claim("preferred_username",user.getEmail()).claim("role","ADMIN"));
    }
    @Test void onlyDatabaseAdminsCanManageOrInspectTracking() throws Exception {
        for(var role:List.of(UserRole.EMPLOYEE,UserRole.PROJECT_ADMIN)) {
            user.setRole(role);
            mvc.perform(post("/announcements").with(token()).contentType("application/json").content(input)).andExpect(status().isForbidden());
            mvc.perform(put("/announcements/"+id).with(token()).contentType("application/json").content(input)).andExpect(status().isForbidden());
            for(String state:List.of("DRAFT","PUBLISHED","ARCHIVED")) mvc.perform(post("/announcements/"+id+"/status").param("status",state).param("version","0").with(token())).andExpect(status().isForbidden());
            mvc.perform(delete("/announcements/"+id).param("version","0").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/announcements/"+id+"/tracking").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/announcements").param("management","true").with(token())).andExpect(status().isForbidden());
            mvc.perform(post("/announcements/"+id+"/open").param("management","true").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/announcements/"+id+"/attachment").param("management","true").with(token())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(db);
    }
    @Test void loginAndActiveAccountAreRequired() throws Exception {
        mvc.perform(get("/announcements")).andExpect(status().isUnauthorized());
        user.setIsActive(false);
        mvc.perform(get("/announcements").with(token())).andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }
    @Test void invalidFieldsAndDatesAreRejected() throws Exception {
        user.setRole(UserRole.ADMIN);
        mvc.perform(post("/announcements").with(token()).contentType("application/json").content(input.replace("\"News\"","\" \""))).andExpect(status().isBadRequest());
        mvc.perform(post("/announcements").with(token()).contentType("application/json").content(input.replace("\"version\":0","\"version\":0,\"expirationDate\":\"2026-09-21\""))).andExpect(status().isBadRequest());
        verifyNoInteractions(db);
    }
}
