package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimesheetReminderEmailServiceTest {
    private final EmailAlertService alerts = mock(EmailAlertService.class);
    @org.junit.jupiter.api.BeforeEach void preferences() {
        when(alerts.preferences(any())).thenReturn(new EmailAlertService.Preferences(true,true,true,true,true,true,true,true));
    }
    @Test void sendsToWorkEmailWithPeriodAndAppLink() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var service = new TimesheetReminderEmailService(provider, "chronos@example.com", "https://chronos.example.com/", alerts);
        service.sendReminder(User.builder().email("employee@example.com").build(), YearMonth.of(2026, 9));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("chronos@example.com", message.getValue().getFrom());
        assertArrayEquals(new String[]{"employee@example.com"}, message.getValue().getTo());
        assertTrue(message.getValue().getSubject().contains("September 2026"));
        assertTrue(message.getValue().getText().contains("https://chronos.example.com/timesheets"));
    }

    @Test void optingOutSkipsReminderEvenWithoutMailConfiguration() {
        when(alerts.preferences(any())).thenReturn(new EmailAlertService.Preferences(false,true,true,true,true,true,true,true));
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var service = new TimesheetReminderEmailService(provider, "", "https://chronos.example.com", alerts);
        service.sendReminder(User.builder().id(1L).build(), YearMonth.of(2026,9));
        verifyNoInteractions(provider);
    }

    @Test void missingConfigurationDoesNotPretendToSend() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var service = new TimesheetReminderEmailService(provider, "", "https://chronos.example.com", alerts);
        assertThrows(IllegalStateException.class, () -> service.sendReminder(User.builder().build(), YearMonth.of(2026, 9)));
    }
}
