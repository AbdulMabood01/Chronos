package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfilePermissionsTest {
    @Test void superAdminCannotSetSsnAndExistingValueIsNotReturnedOrErased() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var user = User.builder().id(1L).email("admin@example.com").role(UserRole.SUPER_ADMIN).ssnLast4("1234").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var request = new UpdateProfileRequest();
        request.setSsnLast4("5678");
        assertThrows(AccessDeniedException.class, () -> service.updateOwnProfile(user.getEmail(), request));
        verify(users, never()).save(any());
        request.setSsnLast4(null);
        when(users.save(user)).thenReturn(user);
        assertNull(service.updateOwnProfile(user.getEmail(), request).getSsnLast4());
        assertEquals("1234", user.getSsnLast4());
        assertNull(service.findByEmail(user.getEmail()).getSsnLast4());
    }

    @Test void employeeCanStillUpdateSsn() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var request = new UpdateProfileRequest();
        request.setSsnLast4("1234");
        assertEquals("1234", service.updateOwnProfile(user.getEmail(), request).getSsnLast4());
    }
}
