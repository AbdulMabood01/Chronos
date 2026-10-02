package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompanyInvitationAcceptanceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final OnboardingService onboarding = mock(OnboardingService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final CompanyManagementService service = new CompanyManagementService(db,
            mock(CompanyAccessService.class), users, mock(InvitationEmailService.class), audit,
            onboarding, passwords);

    private Map<String, Object> invitation(String email) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 8L);
        row.put("company_id", 12L);
        row.put("project_id", null);
        row.put("invitee_email", email);
        row.put("role_key", "COMPANY_ADMIN");
        row.put("created_by_user_id", 3L);
        row.put("accepted_at", null);
        row.put("revoked_at", null);
        return row;
    }

    @Test void acceptingByIdRequiresTheSignedInEmail() {
        when(users.findById(5L)).thenReturn(Optional.of(User.builder().id(5L).email("alice@example.com").build()));
        when(db.queryForList(anyString(), eq(8L), eq("alice@example.com")))
                .thenReturn(List.of(invitation("bob@example.com")));
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(8L))).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> service.acceptById(8L, 5L));
        verifyNoInteractions(audit);
    }

    @Test void acceptingByIdCreatesMembershipOnlyAfterEmailMatches() {
        when(users.findById(5L)).thenReturn(Optional.of(User.builder().id(5L).email("alice@example.com").build()));
        when(db.queryForList(anyString(), eq(8L), eq("alice@example.com")))
                .thenReturn(List.of(invitation("alice@example.com")));
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(8L))).thenReturn(true);

        service.acceptById(8L, 5L);

        verify(db).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO company_memberships"),
                eq(12L), eq(5L));
        verify(db).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO role_assignments"),
                eq(5L), eq("COMPANY_ADMIN"), eq(12L), eq(null), eq(3L));
        verify(db).update(org.mockito.ArgumentMatchers.startsWith("UPDATE company_invitations"), eq(8L));
    }

    @Test void expiredInvitationCannotCreateMembership() {
        when(users.findById(5L)).thenReturn(Optional.of(User.builder().id(5L).email("alice@example.com").build()));
        when(db.queryForList(anyString(), eq(8L), eq("alice@example.com")))
                .thenReturn(List.of(invitation("alice@example.com")));
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(8L))).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.acceptById(8L, 5L));
        verifyNoInteractions(audit);
    }

    @Test void existingAccountMustSignInRatherThanResetItsPasswordWithInvitation() {
        String token = "a".repeat(43);
        when(db.queryForList(anyString(), anyString())).thenReturn(List.of(invitation("alice@example.com")));
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(8L))).thenReturn(true);
        when(users.findByEmailForUpdate("alice@example.com")).thenReturn(Optional.of(
                User.builder().id(5L).email("alice@example.com").passwordHash("existing-hash").build()));

        assertEquals("This email already has an account. Sign in to accept the invitation.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.claimInvitation(token, "Alice", "Smith", "StrongPassword123")).getMessage());
        verifyNoInteractions(onboarding, passwords, audit);
    }

    @Test void unknownWorkspaceDoesNotCreateAnAccessRequest() {
        when(db.queryForList(anyString(), eq(Long.class), eq("unknown-workspace"))).thenReturn(List.of());

        service.requestAccess("unknown-workspace", "Alice", "Smith", "alice@example.com");

        verify(db, never()).update(anyString(), any(), any(), any(), any());
        verifyNoInteractions(audit);
    }
}
