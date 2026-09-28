package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSecurityServiceTest {
    @Test void adminLockAndUnlockRevokeSessionsAndAuditActor() {
        UserRepository users = mock(UserRepository.class);
        AuditService audit = mock(AuditService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        UserService service = new UserService(users, audit, sessions);
        User admin = User.builder().id(1L).role(UserRole.ADMIN).build();
        User employee = User.builder().id(2L).role(UserRole.EMPLOYEE).build();
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findForUpdate(2L)).thenReturn(Optional.of(employee));
        service.lockAccount(2L, 1L, "Security review");
        assertTrue(employee.isAdminLocked());
        assertEquals("Security review", employee.getAdminLockReason());
        verify(sessions).revokeAll(2L);
        verify(audit).logSecurityAction(1L, com.maxwell.chronos.enums.AuditAction.ACCOUNT_LOCKED, 2L);
        service.unlockAccount(2L, 1L);
        assertFalse(employee.isAdminLocked());
        assertEquals(1L, employee.getUnlockedBy());
        verify(audit).logSecurityAction(1L, com.maxwell.chronos.enums.AuditAction.ACCOUNT_UNLOCKED, 2L);
        service.signOutAll(2L, 1L);
        verify(sessions, times(2)).revokeAll(2L);
        verify(audit).logSecurityAction(1L, com.maxwell.chronos.enums.AuditAction.SESSIONS_REVOKED, 2L);
    }

    @Test void otherRolesCannotLockOrUnlock() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class));
        when(users.findById(3L)).thenReturn(Optional.of(User.builder().id(3L).role(UserRole.PROJECT_ADMIN).build()));
        assertThrows(AccessDeniedException.class, () -> service.lockAccount(2L, 3L, null));
        assertThrows(AccessDeniedException.class, () -> service.unlockAccount(2L, 3L));
        assertThrows(AccessDeniedException.class, () -> service.signOutAll(2L, 3L));
        verify(users, never()).findForUpdate(2L);
    }
}
