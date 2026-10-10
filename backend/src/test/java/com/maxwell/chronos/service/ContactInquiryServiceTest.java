package com.maxwell.chronos.service;

import com.maxwell.chronos.dto.ContactInquiryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactInquiryServiceTest {
    private final JavaMailSender mail = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private ContactInquiryService configured() {
        when(provider.getIfAvailable()).thenReturn(mail);
        return new ContactInquiryService(provider, "sales@example.com", "chronos@example.com", "smtp.example.com");
    }
    private ContactInquiryRequest inquiry(String email, String website) {
        return new ContactInquiryRequest(" Jordan Davis ", " Acme ", email, "+1 (312) 555-0100", " Let's discuss our team. ", "LOGIN", website);
    }
    @Test void sendsToConfiguredInboxWithVisitorReplyToAndFullDetails() {
        configured().send(inquiry("jordan@example.com", ""));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"sales@example.com"}, message.getValue().getTo());
        assertEquals("chronos@example.com", message.getValue().getFrom());
        assertEquals("jordan@example.com", message.getValue().getReplyTo());
        assertEquals("Chronos website inquiry", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("Name: Jordan Davis\nCompany: Acme"));
        assertTrue(message.getValue().getText().contains("+1 (312) 555-0100"));
        assertTrue(message.getValue().getText().contains("Login page"));
        assertTrue(message.getValue().getText().contains("Let's discuss our team."));
    }
    @Test void blankDestinationNeverSendsAndReturnsUnavailable() {
        when(provider.getIfAvailable()).thenReturn(mail);
        var service = new ContactInquiryService(provider, "", "chronos@example.com", "smtp.example.com");
        assertFalse(service.available());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.send(inquiry("jordan@example.com", ""))).getStatusCode().value());
        verifyNoInteractions(mail);
    }
    @Test void missingSmtpOrInvalidConfiguredAddressDoesNotEnableForm() {
        when(provider.getIfAvailable()).thenReturn(mail);
        assertFalse(new ContactInquiryService(provider, "sales@example.com", "chronos@example.com", "").available());
        assertFalse(new ContactInquiryService(provider, "not-an-email", "chronos@example.com", "smtp.example.com").available());
        assertFalse(new ContactInquiryService(provider, "sales@example.com", "", "smtp.example.com").available());
        when(provider.getIfAvailable()).thenReturn(null);
        assertFalse(new ContactInquiryService(provider, "sales@example.com", "chronos@example.com", "smtp.example.com").available());
    }
    @Test void honeypotPreventsSending() {
        var service = configured();
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.send(inquiry("jordan@example.com", "spam"))).getStatusCode().value());
        verifyNoInteractions(mail);
    }
    @Test void headerInjectionAndMultipleReplyAddressesAreRejected() {
        var service = configured();
        for (String email : new String[]{"jordan@example.com\r\nBcc: victim@example.com", "jordan@example.com,victim@example.com", "Name <jordan@example.com>"})
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.send(inquiry(email, ""))).getStatusCode().value());
        verifyNoInteractions(mail);
    }
    @Test void smtpFailuresExposeNoProviderDetailsAndAreNotReportedAsSuccess() {
        var service = configured();
        doThrow(new MailSendException("secret SMTP response")).when(mail).send(any(SimpleMailMessage.class));
        var failure = assertThrows(ResponseStatusException.class, () -> service.send(inquiry("jordan@example.com", "")));
        assertEquals(503, failure.getStatusCode().value());
        assertFalse(failure.getReason().contains("secret"));
        assertNull(failure.getCause());
    }
}
