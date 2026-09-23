package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.EmployeeReportService;
import com.maxwell.chronos.web.EmployeeReportController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeReportController.class)
@Import({SecurityConfig.class, EmployeeReportService.class})
class EmployeeReportAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean JdbcTemplate db;
    User user;
    String id = UUID.randomUUID().toString();
    @BeforeEach void setup() {
        user = User.builder().id(7L).email("employee@example.com").role(UserRole.EMPLOYEE)
            .isActive(true).entraId("subject").passwordHash("hash").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("subject").claim("preferred_username", user.getEmail()).claim("role", "SUPER_ADMIN"));
    }
    @Test void employeesAndProjectAdminsCannotReadOrManageEvenWithForgedRoleClaim() throws Exception {
        for (UserRole role : List.of(UserRole.EMPLOYEE, UserRole.ADMIN)) {
            user.setRole(role);
            mvc.perform(get("/employee-reports").with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/employee-reports/" + id).with(token())).andExpect(status().isForbidden());
            mvc.perform(get("/employee-reports/" + id + "/attachments/" + UUID.randomUUID()).with(token())).andExpect(status().isForbidden());
            mvc.perform(patch("/employee-reports/" + id).with(token()).contentType("application/json")
                .content("{\"status\":\"UNDER_REVIEW\",\"note\":\"test\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(db);
    }
    @Test void unauthenticatedAndInactiveAccountsAreDenied() throws Exception {
        mvc.perform(get("/employee-reports")).andExpect(status().isUnauthorized());
        user.setIsActive(false);
        mvc.perform(get("/employee-reports").with(token())).andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }
    @Test void superAdminCanListWithNoStore() throws Exception {
        user.setRole(UserRole.SUPER_ADMIN);
        mvc.perform(get("/employee-reports").with(token())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
        verify(db).queryForList(anyString(), eq(0));
    }
    @Test void submissionValidatesRequiredFieldsAndAcknowledgment() throws Exception {
        var invalid = new MockMultipartFile("report", "", "application/json", "{\"category\":\"OTHER_INCIDENT\",\"subject\":\"\",\"description\":\"x\"}".getBytes());
        mvc.perform(multipart("/employee-reports").file(invalid).with(token())).andExpect(status().isBadRequest());
        verifyNoInteractions(db);
    }
    @Test void receiptOnlyContainsReferenceAndStatus() throws Exception {
        var report = new MockMultipartFile("report", "", "application/json", "{\"category\":\"OTHER_INCIDENT\",\"subject\":\"Concern\",\"description\":\"Details\",\"anonymous\":true,\"privacyAcknowledged\":true}".getBytes());
        mvc.perform(multipart("/employee-reports").file(report).with(token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.reportId").isString()).andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.reporter").doesNotExist()).andExpect(jsonPath("$.description").doesNotExist());
    }
}
