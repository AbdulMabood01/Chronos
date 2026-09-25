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
    @Test void timezoneIsSavedReturnedAndValidated() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var request = new UpdateProfileRequest();
        request.setTimezone("Asia/Kolkata");
        assertEquals("Asia/Kolkata", service.updateOwnProfile(user.getEmail(), request).getTimezone());
        request.setTimezone(null);
        assertEquals("Asia/Kolkata", service.updateOwnProfile(user.getEmail(), request).getTimezone());
        request.setTimezone("Invalid/Timezone");
        assertThrows(IllegalArgumentException.class, () -> service.updateOwnProfile(user.getEmail(), request));
        assertEquals("Asia/Kolkata", user.getTimezone());
    }

    @Test void optionalDetailsAreSavedClearedAndExcludedFromGeneralUserResponses() throws Exception {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var request = new UpdateProfileRequest();
        String[] fields = {"PhoneNumber", "PersonalEmail", "AddressLine1", "AddressLine2", "City", "StateProvince", "PostalCode", "Country", "BloodGroup", "EmergencyContactName", "EmergencyContactRelationship", "EmergencyContactPhone", "EmergencyContactEmail"};
        for (String field : fields) {
            UpdateProfileRequest.class.getMethod("set" + field, String.class).invoke(request, " value ");
        }
        var saved = service.updateOwnProfile(user.getEmail(), request);
        var general = service.findByEmail(user.getEmail());
        for (String field : fields) {
            assertEquals("value", User.class.getMethod("get" + field).invoke(user));
            assertEquals("value", saved.getClass().getMethod("get" + field).invoke(saved));
            assertNull(general.getClass().getMethod("get" + field).invoke(general));
            UpdateProfileRequest.class.getMethod("set" + field, String.class).invoke(request, " ");
        }
        var cleared = service.updateOwnProfile(user.getEmail(), request);
        for (String field : fields) {
            assertNull(User.class.getMethod("get" + field).invoke(user));
            assertNull(cleared.getClass().getMethod("get" + field).invoke(cleared));
        }
    }

    @Test void validatesBloodGroupEmailAndFieldLengths() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new UpdateProfileRequest();
            request.setFirstName("Test");
            request.setLastName("User");
            request.setJobTitle("Engineer");
            request.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
            request.setBloodGroup("O+");
            request.setPersonalEmail("");
            assertTrue(validator.validate(request).isEmpty());
            request.setBloodGroup("X+");
            request.setEmergencyContactEmail("invalid");
            request.setPostalCode("x".repeat(21));
            var invalidFields = validator.validate(request).stream()
                    .map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(invalidFields.containsAll(java.util.Set.of("bloodGroup", "emergencyContactEmail", "postalCode")));
        }
    }

    @Test void systemAdminCannotSetSsnAndExistingValueIsNotReturnedOrErased() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var user = User.builder().id(1L).email("admin@example.com").role(UserRole.ADMIN).ssnLast4("1234").build();
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
    @Test void onlyAdminCanSetOrClearJoiningDate() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class));
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var admin = User.builder().id(2L).role(UserRole.PROJECT_ADMIN).build();
        var systemAdmin = User.builder().id(3L).role(UserRole.ADMIN).build();
        var date = java.time.LocalDate.of(2026, 7, 27);
        assertThrows(AccessDeniedException.class, () -> service.updateJoiningDate(1L, date, employee));
        assertThrows(AccessDeniedException.class, () -> service.updateJoiningDate(1L, date, admin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(users.save(employee)).thenReturn(employee);
        assertEquals(date, service.updateJoiningDate(1L, date, systemAdmin).getJoiningDate());
        assertNull(service.updateJoiningDate(1L, null, systemAdmin).getJoiningDate());
    }
}
