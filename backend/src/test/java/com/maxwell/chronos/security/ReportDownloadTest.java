package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.UserService;
import com.maxwell.chronos.web.ReportController;
import com.maxwell.chronos.service.ReportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportDownloadTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean UserService service;
    @MockitoBean ReportService reports;
    User employee;

    @BeforeEach void setup() {
        employee = User.builder().id(1L).email("employee@example.com").entraId("employee-subject")
                .role(UserRole.EMPLOYEE).isActive(true).build();
        when(users.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(service.findUserEntityByEmail(employee.getEmail())).thenReturn(employee);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("employee-subject").claim("preferred_username", employee.getEmail()));
    }

    @Test void approvedPdfIsAnAttachment() throws Exception {
        when(reports.exportProjectTimesheetPdfById(3L, 1L, false)).thenReturn(new byte[]{37, 80, 68, 70});
        mvc.perform(get("/reports/timesheet-projects/3/pdf").with(token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"project-timesheet-3.pdf\""))
                .andExpect(content().bytes(new byte[]{37, 80, 68, 70}));
    }
    @Test void invalidApprovalHasReadableErrorInsteadOfForbidden() throws Exception {
        when(reports.exportProjectTimesheetPdfById(3L, 1L, false))
                .thenThrow(new IllegalArgumentException("Timesheet must be approved before PDF export"));
        mvc.perform(get("/reports/timesheet-projects/3/pdf").with(token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Timesheet must be approved before PDF export"));
    }
    @Test void unauthorizedPdfRemainsForbidden() throws Exception {
        when(reports.exportProjectTimesheetPdfById(3L, 1L, false))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Not authorized"));
        mvc.perform(get("/reports/timesheet-projects/3/pdf").with(token())).andExpect(status().isForbidden());
    }
}
