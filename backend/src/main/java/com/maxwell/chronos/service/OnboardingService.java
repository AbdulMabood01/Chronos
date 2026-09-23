package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class OnboardingService {
    private final UserRepository users;
    private final EmployeeInvitationRepository invitations;
    private final PasswordEncoder passwords;
    private final InvitationEmailService email;
    private final long hours;
    private final SecureRandom random = new SecureRandom();
    public OnboardingService(UserRepository users, EmployeeInvitationRepository invitations,
            PasswordEncoder passwords, InvitationEmailService email,
            @Value("${chronos.invitation.hours:72}") long hours) {
        if (hours < 1 || hours > 720) throw new IllegalArgumentException("Invitation expiry must be 1–720 hours");
        this.users=users; this.invitations=invitations; this.passwords=passwords; this.email=email; this.hours=hours;
    }
    public User create(String firstName, String lastName, String address) {
        String normalized=address.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(normalized).isPresent()) throw new IllegalArgumentException("An employee with this email already exists");
        return users.saveAndFlush(User.builder().firstName(firstName.trim()).lastName(lastName.trim())
                .email(normalized).employeeId("EMP-"+UUID.randomUUID()).entraId(UUID.randomUUID().toString())
                .role(UserRole.EMPLOYEE).isActive(true).profileCompleted(false).build());
    }
    public void invite(Long id) {
        User user=users.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        if (!"INVITED".equals(user.getAccountStatus())) throw new IllegalArgumentException("Only invited employees can receive an invitation");
        EmployeeInvitation invitation=invitations.findByEmployeeId(id).orElseGet(EmployeeInvitation::new);
        if (invitation.getCreatedAt()!=null && invitation.getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
            throw new IllegalArgumentException("Please wait one minute before resending an invitation");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        invitation.setEmployeeId(id); invitation.setTokenHash(hash(token)); invitation.setCreatedAt(Instant.now());
        invitation.setExpiresAt(Instant.now().plus(Duration.ofHours(hours))); invitation.setUsedAt(null); invitation.setRevokedAt(null);
        invitations.saveAndFlush(invitation);
        email.send(user,token,invitation.getExpiresAt());
    }
    public record InvitationInfo(String firstName, String lastName, String email) {}
    public InvitationInfo validate(String token) {
        Long id=findEmployee(token);
        User user=users.findForUpdate(id).orElseThrow(() -> invalid());
        EmployeeInvitation invitation=invitations.findByEmployeeId(user.getId()).orElseThrow(() -> invalid());
        check(invitation,user,token);
        return new InvitationInfo(user.getFirstName(),user.getLastName(),user.getEmail());
    }
    public void activate(String token, String password) {
        validatePassword(password);
        Long id=findEmployee(token);
        User user=users.findForUpdate(id).orElseThrow(() -> invalid());
        EmployeeInvitation invitation=invitations.findByEmployeeId(user.getId()).orElseThrow(() -> invalid());
        check(invitation,user,token);
        user.setPasswordHash(passwords.encode(password));
        if(user.getEntraId()==null) user.setEntraId(UUID.randomUUID().toString());
        invitation.setUsedAt(Instant.now());
        users.save(user); invitations.save(invitation);
    }
    public void revoke(Long id) {
        users.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        invitations.findByEmployeeId(id).ifPresent(invitation -> { invitation.setRevokedAt(Instant.now()); invitations.save(invitation); });
    }
    private Long findEmployee(String token) { return invitations.findEmployeeIdByTokenHash(hash(token)).orElseThrow(() -> invalid()); }
    private void check(EmployeeInvitation invitation, User user, String token) {
        if (!invitation.getTokenHash().equals(hash(token))) throw invalid();
        if (invitation.getUsedAt()!=null) throw new IllegalArgumentException("This invitation has already been used. Please sign in.");
        if (invitation.getRevokedAt()!=null || !Boolean.TRUE.equals(user.getIsActive())) throw new IllegalArgumentException("This invitation has been revoked. Contact your administrator.");
        if (!invitation.getExpiresAt().isAfter(Instant.now())) throw new IllegalArgumentException("This invitation has expired. Ask your administrator for a new invitation.");
        if (user.getPasswordHash()!=null) throw invalid();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid invitation. Ask your administrator for a new invitation."); }
    static String hash(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid invitation");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static void validatePassword(String password) {
        if(password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72
                || !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[0-9].*"))
            throw new IllegalArgumentException("Use at least 12 characters with uppercase, lowercase and a number (maximum 72 UTF-8 bytes).");
    }
}
