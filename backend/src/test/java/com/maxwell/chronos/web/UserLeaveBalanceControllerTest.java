package com.maxwell.chronos.web;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.LeaveBalanceDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.service.LeaveBalanceService;
import com.maxwell.chronos.service.ProjectService;
import com.maxwell.chronos.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLeaveBalanceControllerTest {
    @Mock UserService users;
    @Mock ProjectService projects;
    @Mock LeaveBalanceService balances;
    @InjectMocks UserController controller;

    @Test void ownBalanceUsesAuthenticatedIdentity() {
        var employee = User.builder().id(12L).role(UserRole.EMPLOYEE).build();
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .claim("preferred_username", "employee@example.com").build();
        when(users.findUserEntityByEmail("employee@example.com")).thenReturn(employee);



        org.junit.jupiter.api.Assertions.assertEquals(410, org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.getMyLeaveBalance(2026,jwt)).getStatusCode().value());
        verifyNoInteractions(balances);
    }
}
