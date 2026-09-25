package com.maxwell.chronos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmailAlertDispatcherTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    EmailAlertService alerts = mock(EmailAlertService.class);
    JavaMailSender mail = mock(JavaMailSender.class);
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    EmailAlertDispatcher dispatcher = new EmailAlertDispatcher(db,alerts,provider,"chronos@example.com","https://chronos.example.com/");
    @BeforeEach void setup() {
        when(provider.getIfAvailable()).thenReturn(mail);
        when(alerts.preferences(1L)).thenReturn(new EmailAlertService.Preferences(true,true,true,true,true,true,true,true));
        when(db.queryForList(contains("FROM email_alert_outbox"))).thenReturn(List.of(Map.of(
            "id",7L,"user_id",1L,"category","FEEDBACK","email","employee@example.com","is_active",true,
            "subject","Chronos: feedback received","path","/feedback","attempts",0)));
    }
    @Test void sendsOnlyGenericNoticeToRecipientAndCompletesJob() {
        dispatcher.deliver();
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"employee@example.com"},message.getValue().getTo());
        assertEquals("Chronos: feedback received",message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("https://chronos.example.com/feedback"));
        verify(db).update(contains("completed_at=CURRENT_TIMESTAMP"),eq(7L));
    }
    @Test void honorsPreferencesChangedSinceTheEventWasQueued() {
        when(alerts.preferences(1L)).thenReturn(new EmailAlertService.Preferences(false,true,true,true,true,true,true,true));
        dispatcher.deliver();
        verifyNoInteractions(mail);
        verify(db).update(contains("completed_at=CURRENT_TIMESTAMP"),eq(7L));
    }
    @Test void smtpFailureSchedulesRetryWithoutThrowing() {
        doThrow(new MailSendException("offline")).when(mail).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(dispatcher::deliver);
        verify(db).update(contains("attempts=attempts+1"),eq(7L));
        verify(db,never()).update(contains("completed_at=CURRENT_TIMESTAMP"),eq(7L));
    }
    @Test void missingMailConfigurationKeepsEventsQueued() {
        new EmailAlertDispatcher(db,alerts,provider,"","https://chronos.example.com").deliver();
        verifyNoInteractions(mail);
        verify(db,never()).queryForList(contains("FROM email_alert_outbox"));
    }
    @Test void visibleAnnouncementVersionsAreQueuedOncePerActiveAudience() {
        when(db.queryForList(contains("FROM company_announcements"))).thenReturn(List.of(Map.of("id","announcement","version",1)));
        when(db.queryForList(contains("SELECT id FROM users"),eq(Long.class))).thenReturn(List.of(1L,2L));
        dispatcher.deliver();
        verify(alerts).enqueue(1L,EmailAlertService.Category.ANNOUNCEMENTS,"Chronos: announcement published or updated","/announcements");
        verify(alerts).enqueue(2L,EmailAlertService.Category.ANNOUNCEMENTS,"Chronos: announcement published or updated","/announcements");
        verify(db).update(contains("emailed_version=version"),eq("announcement"));
    }
}
