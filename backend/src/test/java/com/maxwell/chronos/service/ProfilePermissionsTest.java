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
    @Test void submittedIdentityCannotBeChanged() {
        var users=mock(UserRepository.class);
        var user=User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE)
            .firstName("Test").lastName("Employee").dateOfBirth(java.time.LocalDate.of(1990,1,1)).profileCompleted(true).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));
        var service=new UserService(users,mock(AuditService.class),mock(AuthSessionService.class));
        var request=new UpdateProfileRequest();request.setFirstName("Changed");request.setLastName("Employee");request.setDateOfBirth(user.getDateOfBirth());
        assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        request.setFirstName("Test");request.setDateOfBirth(java.time.LocalDate.of(1991,1,1));
        assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        request.setDateOfBirth(user.getDateOfBirth());user.setProfileDetailsSubmitted(true);user.setGender("Female");request.setGender("Male");
        assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        verify(users,never()).save(any());
    }
    @Test void timezoneIsSavedReturnedAndValidated() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));
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

    @Test void contactDetailsAreSavedClearedAndExcludedFromGeneralUserResponses() throws Exception {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var request = new UpdateProfileRequest();
        String[] fields = {"PhoneNumber", "PersonalEmail", "AddressLine1", "AddressLine2", "City", "StateProvince", "PostalCode", "Country", "EmergencyContactName", "EmergencyContactRelationship", "EmergencyContactPhone", "EmergencyContactEmail"};
        for (String field : fields) {
            UpdateProfileRequest.class.getMethod("set" + field, String.class).invoke(request, " value ");
        }
        request.setBloodGroup("O+");
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
            request.setGender("Other");request.setRace("Asian");request.setEthnicity("Prefer not to say");
            request.setJoiningDate(java.time.LocalDate.of(2024,1,1));
            request.setPhoneNumber("5550100");request.setPersonalEmail("test@example.com");
            request.setAddressLine1("12 Main St");request.setCity("Chicago");request.setStateProvince("Illinois");request.setPostalCode("60601");request.setCountry("USA");
            request.setEmergencyContactName("Alex");request.setEmergencyContactRelationship("Sibling");request.setEmergencyContactPhone("5550101");request.setEmergencyContactEmail("alex@example.com");
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
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(Boolean.class),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq("PLATFORM_ADMIN"))).thenReturn(true);
        var service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class),jdbc);
        var user = User.builder().id(1L).email("admin@example.com").role(UserRole.ADMIN).ssnLast4("1234").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));
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
        var service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class));
        var user = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        var request = new UpdateProfileRequest();
        request.setSsnLast4("1234");
        assertEquals("1234", service.updateOwnProfile(user.getEmail(), request).getSsnLast4());
    }
    @Test void legacyJoiningDateEditsAreBlockedForEveryRole() {
        var users = mock(UserRepository.class);
        var service = new UserService(users, mock(AuditService.class), mock(AuthSessionService.class));
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var admin = User.builder().id(2L).role(UserRole.PROJECT_ADMIN).build();
        var systemAdmin = User.builder().id(3L).role(UserRole.ADMIN).build();
        var date = java.time.LocalDate.of(2026, 7, 27);
        for(var actor:java.util.List.of(employee,admin,systemAdmin)) {
            var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateJoiningDate(1L,date,actor));
            assertEquals(410,ex.getStatusCode().value());
        }
        verifyNoInteractions(users);
    }

    @Test void personalProfileCannotChangeLegacyEmploymentFields() {
        var users=mock(UserRepository.class);
        var service=new UserService(users,mock(AuditService.class),mock(AuthSessionService.class));
        var request=new UpdateProfileRequest();request.setJobTitle("New title");
        assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile("employee@example.com",request));
        verifyNoInteractions(users);
    }
    @Test void requiredProfileDetailsCannotBeOmittedOrBlank() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = new UpdateProfileRequest();
            var fields = factory.getValidator().validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(fields.containsAll(java.util.Set.of("gender", "race", "ethnicity", "joiningDate", "phoneNumber", "personalEmail", "addressLine1", "city", "stateProvince", "postalCode", "country", "emergencyContactName", "emergencyContactRelationship", "emergencyContactPhone", "emergencyContactEmail")));
            request.setPhoneNumber("   ");
            assertTrue(factory.getValidator().validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber")));
            assertFalse(fields.contains("addressLine2"));
        }
    }
    @Test void submittedMissingDetailsCanBeCompletedButExistingChoicesStayLocked() {
        var users=mock(UserRepository.class);
        var user=User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).profileDetailsSubmitted(true).race("Asian").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(users.findForUpdate(1L)).thenReturn(Optional.of(user));when(users.save(user)).thenReturn(user);
        var service=new UserService(users,mock(AuditService.class),mock(AuthSessionService.class));
        var request=new UpdateProfileRequest();request.setGender("Other");request.setRace("Asian");request.setEthnicity("Prefer not to say");request.setJoiningDate(java.time.LocalDate.of(2024,1,1));
        assertEquals("Other",service.updateOwnProfile(user.getEmail(),request).getGender());
        request.setRace("White");assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
    }
    @Test void invalidDemographicChoicesAreRejected() {
        var users=mock(UserRepository.class);
        var user=User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(users.findForUpdate(1L)).thenReturn(Optional.of(user));
        var service=new UserService(users,mock(AuditService.class),mock(AuthSessionService.class));var request=new UpdateProfileRequest();
        request.setGender("invalid");assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        request.setGender("Male");request.setRace("invalid");assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        request.setRace("Asian");request.setEthnicity("invalid");assertThrows(IllegalArgumentException.class,()->service.updateOwnProfile(user.getEmail(),request));
        verify(users,never()).save(any());
    }
}
