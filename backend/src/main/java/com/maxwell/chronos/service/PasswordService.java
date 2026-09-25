package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@Transactional
@RequiredArgsConstructor
public class PasswordService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordService.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final InvitationEmailService email;
    private final SecureRandom random = new SecureRandom();

    public void forgot(String address) {
        var found = users.findIdByEmailIgnoreCase(address.trim());
        if (found.isEmpty()) return;
        User user = users.findForUpdate(found.get()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getAccountStatus())) return;
        Instant now = Instant.now();
        if (user.getPasswordResetRequestedAt() != null
                && user.getPasswordResetRequestedAt().isAfter(now.minusSeconds(60))) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = now.plusSeconds(1800);
        // A delivery failure must not reveal whether an email belongs to an account.
        try { email.sendPasswordReset(user, token, expires); }
        catch (IllegalStateException | IllegalArgumentException ex) {
            log.warn("Password reset email could not be sent. Check SMTP and frontend URL configuration.");
            return;
        }
        user.setPasswordResetHash(OnboardingService.hash(token));
        user.setPasswordResetExpiresAt(expires);
        user.setPasswordResetRequestedAt(now);
        users.save(user);
    }

    public void validate(String token) { resetUser(token); }

    public void reset(String token, String password, String confirmation) {
        validateNewPassword(password, confirmation);
        update(resetUser(token), password);
    }

    public void change(String subject, String current, String password, String confirmation) {
        validateNewPassword(password, confirmation);
        Long id = users.findIdByEntraId(subject).orElseThrow(() -> new IllegalArgumentException("Account unavailable."));
        User user = users.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Account unavailable."));
        if (!"ACTIVE".equals(user.getAccountStatus()) || current == null || !passwords.matches(current, user.getPasswordHash()))
            throw new IllegalArgumentException("Current password is incorrect.");
        update(user, password);
    }

    private User resetUser(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = OnboardingService.hash(token);
        Long id = users.findIdByPasswordResetHash(hash).orElseThrow(PasswordService::invalid);
        User user = users.findForUpdate(id).orElseThrow(PasswordService::invalid);
        // Recheck after locking: concurrent requests can consume or replace the token.
        if (!"ACTIVE".equals(user.getAccountStatus()) || !hash.equals(user.getPasswordResetHash())
                || user.getPasswordResetExpiresAt() == null || !user.getPasswordResetExpiresAt().isAfter(Instant.now())) throw invalid();
        return user;
    }

    private void validateNewPassword(String password, String confirmation) {
        OnboardingService.validatePassword(password);
        if (!password.equals(confirmation)) throw new IllegalArgumentException("Passwords do not match.");
    }

    private void update(User user, String password) {
        if (passwords.matches(password, user.getPasswordHash()))
            throw new IllegalArgumentException("Choose a password different from your current password.");
        user.setPasswordHash(passwords.encode(password));
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        user.setPasswordResetHash(null);
        user.setPasswordResetExpiresAt(null);
        users.save(user);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("This reset link is invalid or expired. Request a new reset link.");
    }
}
