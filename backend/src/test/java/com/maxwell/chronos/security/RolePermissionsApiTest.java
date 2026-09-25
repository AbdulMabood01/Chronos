package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import com.maxwell.chronos.service.*;
import com.maxwell.chronos.web.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = {ProjectController.class, TimesheetController.class, VacationController.class,
        AuthController.class, UserController.class}, properties = {"logging.level.root=WARN", "logging.level.org.springframework=WARN"})
@Import({SecurityConfig.class, ProjectService.class, TimesheetService.class, VacationService.class, TeamCalendarService.class})
class RolePermissionsApiTest {
    @MockitoBean com.maxwell.chronos.service.LeaveBalanceService leaveBalances;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserService userService;
    @MockitoBean UserRepository users;
    @MockitoBean ProjectRepository projects;
    @MockitoBean ProjectAssignmentRepository assignments;
    @MockitoBean ProjectHourPlanRepository plans;
    @MockitoBean TimesheetRepository timesheets;
    @MockitoBean TimesheetProjectSubmissionRepository submissions;
    @MockitoBean TimeEntryRepository entries;
    @MockitoBean VacationRequestRepository vacations;
    @MockitoBean AuditService audit;
    @MockitoBean NotificationService notifications;
    User user;

    @BeforeEach void setup() {
        user = User.builder().id(1L).email("admin@example.com").entraId("subject")
                .role(UserRole.ADMIN).isActive(true).passwordHash("test-account-hash").ssnLast4("1234").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userService.findUserEntityByEmail(user.getEmail())).thenReturn(user);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("subject").claim("preferred_username", user.getEmail()));
    }

    @Test void teamCalendarRequiresManagerPermission() throws Exception {
        mvc.perform(get("/vacation/team-calendar").param("year", "2026").param("month", "9").with(token())).andExpect(status().isOk());
        user.setRole(UserRole.EMPLOYEE);
        mvc.perform(get("/vacation/team-calendar").param("year", "2026").param("month", "9").with(token())).andExpect(status().isForbidden());
        when(projects.existsByProjectManagerIdOrProjectManagerHoursApproverId(1L,1L)).thenReturn(true);
        mvc.perform(get("/vacation/team-calendar").param("year", "2026").param("month", "9").with(token())).andExpect(status().isForbidden());
        when(projects.existsByProjectManagerId(1L)).thenReturn(true);
        mvc.perform(get("/vacation/team-calendar").param("year", "2026").param("month", "9").with(token())).andExpect(status().isOk());
    }

    @Test void systemAdminReadsButAllProjectWriteEndpointsReturnForbidden() throws Exception {
        mvc.perform(get("/projects").with(token())).andExpect(status().isOk());
        mvc.perform(get("/projects/hours-dashboard").param("year", "2026").param("month", "9").with(token()))
                .andExpect(status().isOk());
        mvc.perform(post("/projects").contentType("application/json").content("{}").with(token())).andExpect(status().isForbidden());
        mvc.perform(put("/projects/10").contentType("application/json").content("{}").with(token())).andExpect(status().isForbidden());
        mvc.perform(post("/projects/10/assignments/3").param("startDate", "2026-09-01").param("endDate", "2026-09-30")
                .param("billRate", "10").param("plannedHours", "40").with(token())).andExpect(status().isForbidden());
        mvc.perform(delete("/projects/10/assignments/3").with(token())).andExpect(status().isForbidden());
        mvc.perform(patch("/projects/10/assignments/3/dates").param("startDate", "2026-09-01").param("endDate", "2026-09-30")
                .param("billRate", "10").param("plannedHours", "40").with(token())).andExpect(status().isForbidden());
        mvc.perform(patch("/projects/10/assignments/3/planned-hours").param("plannedHours", "10").with(token())).andExpect(status().isForbidden());
        mvc.perform(patch("/projects/10/assignments/3/planned-hours").param("plannedHours", "10")
                .param("year", "2026").param("month", "9").with(token())).andExpect(status().isForbidden());
        verify(projects, never()).save(any());
        verify(assignments, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test void systemAdminSubmissionEndpointsReturnForbiddenForExistingDrafts() throws Exception {
        when(timesheets.findForUpdate(2L)).thenReturn(Optional.of(Timesheet.builder().id(2L).user(user).status(TimesheetStatus.DRAFT).build()));
        when(vacations.findById(3L)).thenReturn(Optional.of(VacationRequest.builder().id(3L).user(user).status(VacationStatus.DRAFT).build()));
        mvc.perform(post("/timesheets/2/submit").with(token())).andExpect(status().isForbidden());
        mvc.perform(post("/timesheets/2/projects/10/submit").with(token())).andExpect(status().isForbidden());
        mvc.perform(post("/vacation/3/submit").with(token())).andExpect(status().isForbidden());
    }

    @Test void authResponseHidesAdminSsnAndExposesEmployeeProjectCapability() throws Exception {
        mvc.perform(get("/auth/me").with(token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ssnLast4").doesNotExist());
        user.setRole(UserRole.EMPLOYEE);
        when(projects.existsByProjectManagerIdOrProjectManagerHoursApproverId(1L, 1L)).thenReturn(true);
        when(projects.existsByProjectManagerId(1L)).thenReturn(true);
        mvc.perform(get("/auth/me").with(token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.canReviewProjects").value(true))
                .andExpect(jsonPath("$.canManageProjects").value(true));
    }

    @Test void removedGlobalRoleCannotBeAssigned() throws Exception {
        mvc.perform(patch("/users/2/role").param("role", "PROJECT_MANAGER").with(token())).andExpect(status().isBadRequest());
        verify(userService, never()).changeRole(any(), any(), any());
    }
    @Test void ordinaryEmployeeCannotReadManagementEndpoints() throws Exception {
        user.setRole(UserRole.EMPLOYEE);
        mvc.perform(get("/projects").with(token())).andExpect(status().isForbidden());
        mvc.perform(get("/projects/hours-dashboard").param("year", "2026").param("month", "9").with(token())).andExpect(status().isForbidden());
        mvc.perform(get("/vacation/pending").with(token())).andExpect(status().isForbidden());
    }
}
