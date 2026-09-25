package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetEmailTest {
    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    JavaMailSender sender = mock(JavaMailSender.class);
    User user = User.builder().email("alice@example.com").firstName("Alice").lastName("Example").build();
    @Test void sendsOneTimeResetLinkToAccountEmail() {
        when(provider.getIfAvailable()).thenReturn(sender);
        new InvitationEmailService(provider, "chronos@example.com", "https://chronos.example.com/")
                .sendPasswordReset(user, "secret", Instant.parse("2026-09-24T12:00:00Z"));
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        var message = captor.getValue();
        assertArrayEquals(new String[]{user.getEmail()}, message.getTo());
        assertEquals("Reset your Chronos password", message.getSubject());
        assertTrue(message.getText().contains("https://chronos.example.com/reset-password#token=secret"));
        assertTrue(message.getText().contains("30 minutes"));
        assertTrue(message.getText().contains("2026-09-24T12:00:00Z"));
    }
    @Test void rejectsInsecureProductionUrlsAndMissingSmtp() {
        when(provider.getIfAvailable()).thenReturn(sender);
        assertThrows(IllegalStateException.class, () -> new InvitationEmailService(provider, "chronos@example.com", "http://chronos.example.com")
                .sendPasswordReset(user, "secret", Instant.now()));
        when(provider.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> new InvitationEmailService(provider, "chronos@example.com", "https://chronos.example.com")
                .sendPasswordReset(user, "secret", Instant.now()));
        verifyNoInteractions(sender);
    }
}
