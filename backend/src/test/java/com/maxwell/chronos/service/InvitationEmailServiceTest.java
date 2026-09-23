package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InvitationEmailServiceTest {
    @Test void emailContainsIdentityConfiguredLinkAndExpiration() {
        JavaMailSender sender=mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(sender);
        var service=new InvitationEmailService(provider,"chronos@example.com","https://chronos.example.com/");
        Instant expiry=Instant.parse("2030-01-01T00:00:00Z");
        service.send(User.builder().firstName("Alice").lastName("Smith").email("alice@example.com").build(),"test-token",expiry);
        var captor=ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(captor.capture());
        var message=captor.getValue(); assertEquals("alice@example.com",message.getTo()[0]);
        assertEquals("chronos@example.com",message.getFrom());
        assertTrue(message.getText().contains("Alice Smith"));
        assertTrue(message.getText().contains("https://chronos.example.com/activate?token=test-token"));
        assertTrue(message.getText().contains(expiry.toString()));
    }
    @Test void smtpErrorsDoNotExposeMessageOrCredentials() {
        JavaMailSender sender=mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("sensitive SMTP detail")).when(sender).send(any(SimpleMailMessage.class));
        var service=new InvitationEmailService(provider,"chronos@example.com","https://chronos.example.com");
        var ex=assertThrows(IllegalStateException.class,()->service.send(User.builder().email("alice@example.com").build(),"secret",Instant.now()));
        assertFalse(ex.getMessage().contains("sensitive")); assertNull(ex.getCause());
    }
}
