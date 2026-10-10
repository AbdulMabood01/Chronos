package com.maxwell.chronos.service;

import com.maxwell.chronos.dto.ContactInquiryRequest;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactInquiryService {
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String recipient;
    private final String from;
    private final String host;

    public ContactInquiryService(ObjectProvider<JavaMailSender> mailProvider,
            @Value("${chronos.contact.inquiry-to:}") String recipient,
            @Value("${chronos.mail.from:}") String from,
            @Value("${spring.mail.host:}") String host) {
        this.mailProvider = mailProvider;
        this.recipient = recipient.strip();
        this.from = from.strip();
        this.host = host.strip();
    }

    public boolean available() {
        return !host.isBlank() && validAddress(recipient) && validAddress(from) && mailProvider.getIfAvailable() != null;
    }

    public void send(ContactInquiryRequest inquiry) {
        if (inquiry.website() != null && !inquiry.website().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inquiry could not be submitted");
        if (!validAddress(inquiry.email().strip()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email");
        if (!available())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Contact inquiries are not available yet");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setReplyTo(inquiry.email().strip());
        message.setSubject("Chronos website inquiry");
        message.setText("A visitor would like to discuss Chronos. Reply to this email to arrange a conversation.\n\n"
            + "Name: " + inquiry.name().strip() + "\n"
            + "Company: " + inquiry.company().strip() + "\n"
            + "Email: " + inquiry.email().strip() + "\n"
            + "Phone: " + (inquiry.phone() == null || inquiry.phone().isBlank() ? "Not provided" : inquiry.phone().strip()) + "\n"
            + "Sent from: " + ("LOGIN".equals(inquiry.source()) ? "Login page" : "Landing page") + "\n\n"
            + "Message:\n" + inquiry.message().strip());
        try { mailProvider.getIfAvailable().send(message); }
        catch (MailException exception) {
            // No inquiry details, provider response or SMTP credentials are logged.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Your inquiry could not be sent. Please try again later.");
        }
    }

    private static boolean validAddress(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return false;
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return address.getPersonal() == null && value.equals(address.getAddress());
        } catch (jakarta.mail.internet.AddressException exception) { return false; }
    }
}
