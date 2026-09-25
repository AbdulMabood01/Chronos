package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.net.URI;

@Service
public class InvitationEmailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final String frontend;
    public InvitationEmailService(ObjectProvider<JavaMailSender> sender,
            @Value("${chronos.mail.from:}") String from,
            @Value("${chronos.frontend-url:}") String frontend) {
        this.sender = sender; this.from = from; this.frontend = frontend;
    }
    public void send(User user, String token, Instant expires) {
        send(user, token, expires, false);
    }
    public void sendPasswordReset(User user, String token, Instant expires) {
        send(user, token, expires, true);
    }
    private void send(User user, String token, Instant expires, boolean reset) {
        JavaMailSender mail = sender.getIfAvailable();
        URI uri = URI.create(frontend);
        if (mail == null || from.isBlank() || uri.getHost() == null
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Configure SMTP, MAIL_FROM and an HTTPS FRONTEND_URL before sending invitations");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail());
        message.setSubject("Welcome to Chronos — activate your account");
        message.setText("Hello " + user.getFullName() + ",\n\nWelcome to Maxwell's Chronos employee workspace.\n"
                + "Create your password and activate your account using this one-time link:\n\n"
                + frontend.replaceAll("/+$", "") + "/activate?token=" + token
                + "\n\nThis invitation expires at " + expires + " (UTC). After activation, sign in with your work email and password."
                + "\nIf this invitation has expired, contact your Admin for a new one."
                + "\nIf you were not expecting this invitation, please contact your administrator.\n\nThe Chronos team");
        if (reset) {
            message.setSubject("Reset your Chronos password");
            message.setText("A password reset was requested for your Chronos account.\n\n"
                    + "Choose a new password using this one-time link:\n"
                    + frontend.replaceAll("/+$", "") + "/reset-password#token=" + token
                    + "\n\nThis link expires at " + expires + " (UTC), in 30 minutes."
                    + "\nIf you did not request this, you can ignore this email. Your password has not changed.");
        }
        // Never log the message, token, SMTP exceptions or provider response.
        try { mail.send(message); }
        catch (org.springframework.mail.MailException ex) {
            throw new IllegalStateException("Invitation email could not be sent. Check SMTP configuration and retry.");
        }
    }
}
