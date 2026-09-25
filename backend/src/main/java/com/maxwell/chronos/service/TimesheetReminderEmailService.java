package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class TimesheetReminderEmailService {
    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String appUrl;
    private final EmailAlertService alerts;

    public TimesheetReminderEmailService(ObjectProvider<JavaMailSender> senderProvider,
            @Value("${chronos.mail.from:}") String from,
            @Value("${chronos.app-url:http://localhost:5173}") String appUrl, EmailAlertService alerts) {
        this.senderProvider = senderProvider;
        this.from = from;
        this.appUrl = appUrl;
        this.alerts = alerts;
    }

    public void sendReminder(User user, YearMonth period) {
        if (!alerts.preferences(user.getId()).allows(EmailAlertService.Category.TIMESHEETS)) return;
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new IllegalStateException("Configure spring.mail.host and chronos.mail.from to send timesheet reminders");
        }
        String month = period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Chronos: submit your " + month + " timesheet");
        message.setText("Please review and submit your " + month + " timesheet in Chronos.\n\n"
                + "Open Chronos: " + appUrl.replaceAll("/+$", "") + "/timesheets\n\n"
                + "Thank you.");
        sender.send(message);
    }
}
