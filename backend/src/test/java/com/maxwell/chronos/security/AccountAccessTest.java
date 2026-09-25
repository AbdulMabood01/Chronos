package com.maxwell.chronos.security;

import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.UserService;
import com.maxwell.chronos.web.UserController;
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

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class AccountAccessTest {
    @MockitoBean com.maxwell.chronos.service.LeaveBalanceService leaveBalances;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserRepository users;
    @MockitoBean UserService service;
    @MockitoBean com.maxwell.chronos.service.ProjectService projects;
    User employee;

    @BeforeEach void setup() {
        employee = User.builder().id(1L).email("employee@example.com").entraId("employee-subject")
                .role(UserRole.EMPLOYEE).isActive(true).passwordHash("test-account-hash").build();
        when(users.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(service.findUserEntityByEmail(employee.getEmail())).thenReturn(employee);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject("employee-subject").claim("preferred_username", employee.getEmail()));
    }

    @Test void anonymousUserCannotReadDirectory() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test void directoryOnlyReturnsEmployeesFromVisibleProjects() throws Exception {
        when(projects.visibleEmployeeIds(employee)).thenReturn(java.util.Set.of(2L));
        when(service.getEmployeeDirectory()).thenReturn(java.util.List.of(
                new com.maxwell.chronos.dto.EmployeeDirectoryDTO(2L, "E2", "Team", "Member", "team@example.com", null, UserRole.EMPLOYEE, true),
                new com.maxwell.chronos.dto.EmployeeDirectoryDTO(3L, "E3", "Other", "Member", "other@example.com", null, UserRole.EMPLOYEE, true)));
        mvc.perform(get("/users").with(token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(2));
        employee.setRole(UserRole.PROJECT_ADMIN);
        mvc.perform(get("/users").with(token())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void employeeCannotReadAnotherProfile() throws Exception {
        mvc.perform(get("/users/2").with(token())).andExpect(status().isForbidden());
        verify(service, never()).findById(2L);
    }

    @Test void employeeCanReadOwnProfile() throws Exception {
        when(service.findById(1L)).thenReturn(UserDTO.builder().id(1L).build());
        mvc.perform(get("/users/1").with(token())).andExpect(status().isOk());
    }

    @Test void existingTokenStopsWorkingAfterDeactivation() throws Exception {
        employee.setIsActive(false);
        mvc.perform(get("/users").with(token())).andExpect(status().isForbidden());
        verify(service, never()).getEmployeeDirectory();
    }

    @Test void emailCannotBeUsedWithAnotherSubject() throws Exception {
        mvc.perform(get("/users").with(jwt().jwt(j -> j.subject("someone-else")
                .claim("preferred_username", employee.getEmail())))).andExpect(status().isForbidden());
    }
}
