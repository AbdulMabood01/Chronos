package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordServiceTest {
    UserRepository users = mock(UserRepository.class);
    InvitationEmailService email = mock(InvitationEmailService.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    PasswordService service = new PasswordService(users, encoder, email);
    User user;
    static final String OLD = "OldPassword123", NEXT = "NewPassword456";
    @BeforeEach void setup() {
        user = User.builder().id(1L).email("alice@example.com").entraId("subject").isActive(true)
                .passwordHash(encoder.encode(OLD)).build();
        when(users.findIdByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user.getId()));
        when(users.findIdByEntraId("subject")).thenReturn(Optional.of(user.getId()));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(user));
    }
    String issue() {
        service.forgot(user.getEmail());
        var token = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).sendPasswordReset(eq(user), token.capture(), any());
        String raw = token.getValue();
        when(users.findIdByPasswordResetHash(OnboardingService.hash(raw))).thenReturn(Optional.of(1L));
        return raw;
    }
    @Test void resetIsHashedExpiringSingleUseAndRevokesSessions() {
        String token = issue();
        assertNotEquals(token, user.getPasswordResetHash());
        assertEquals(64, user.getPasswordResetHash().length());
        assertTrue(user.getPasswordResetExpiresAt().isAfter(Instant.now().plusSeconds(1790)));
        service.validate(token);
        assertTrue(encoder.matches(OLD, user.getPasswordHash()));
        service.reset(token, NEXT, NEXT);
        assertTrue(encoder.matches(NEXT, user.getPasswordHash()));
        assertEquals(1, user.getCredentialVersion());
        assertNull(user.getPasswordResetHash());
        assertThrows(IllegalArgumentException.class, () -> service.reset(token, OLD, OLD));
    }
    @Test void expiredMalformedAndInactiveLinksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.validate("bad"));
        String token = issue();
        user.setPasswordResetExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.reset(token, NEXT, NEXT));
        user.setPasswordResetExpiresAt(Instant.now().plusSeconds(60));
        user.setIsActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.validate(token));
        assertTrue(encoder.matches(OLD, user.getPasswordHash()));
    }
    @Test void unknownInactiveAndInvitedAccountsReceiveNoEmail() {
        service.forgot("unknown@example.com");
        user.setIsActive(false); service.forgot(user.getEmail());
        user.setIsActive(true); user.setPasswordHash(null); service.forgot(user.getEmail());
        verifyNoInteractions(email);
    }
    @Test void deliveryFailureDoesNotExposeAccountOrPersistToken() {
        doThrow(new IllegalStateException("SMTP unavailable")).when(email).sendPasswordReset(any(), any(), any());
        assertDoesNotThrow(() -> service.forgot(user.getEmail()));
        assertNull(user.getPasswordResetHash());
    }
    @Test void requestsAreThrottledAndNewLinkReplacesOldLink() {
        String old = issue();
        service.forgot(user.getEmail());
        verify(email, times(1)).sendPasswordReset(any(), any(), any());
        user.setPasswordResetRequestedAt(Instant.now().minusSeconds(61));
        String replacement = issue();
        assertNotEquals(old, replacement);
        assertThrows(IllegalArgumentException.class, () -> service.validate(old));
        service.validate(replacement);
    }
    @Test void changeRequiresCurrentPasswordAndInvalidatesResetLink() {
        String token = issue();
        assertThrows(IllegalArgumentException.class, () -> service.change("subject", "wrong", NEXT, NEXT));
        assertTrue(encoder.matches(OLD, user.getPasswordHash()));
        service.change("subject", OLD, NEXT, NEXT);
        assertTrue(encoder.matches(NEXT, user.getPasswordHash()));
        assertEquals(1, user.getCredentialVersion());
        assertThrows(IllegalArgumentException.class, () -> service.validate(token));
    }
    @Test void weakMismatchedAndReusedPasswordsAreRejectedWithoutConsumingLink() {
        String token = issue();
        for (String invalid : new String[]{"short", "lowercase12345", "UPPERCASE12345", "NoNumbersHere", "Aa1" + "é".repeat(35)})
            assertThrows(IllegalArgumentException.class, () -> service.reset(token, invalid, invalid));
        assertThrows(IllegalArgumentException.class, () -> service.reset(token, NEXT, OLD));
        assertThrows(IllegalArgumentException.class, () -> service.reset(token, OLD, OLD));
        service.validate(token);
    }
}
