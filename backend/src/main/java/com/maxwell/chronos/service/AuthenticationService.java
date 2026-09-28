package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.Clock;
import java.util.*;

@Service
public class AuthenticationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final String issuer, audience, dummyHash;
    private final long lifetime;
    private final AuthSessionService sessions;
    private final Clock clock;
    public AuthenticationService(UserRepository users, PasswordEncoder passwords, SecretKey key,
            @Value("${chronos.jwt.issuer}") String issuer, @Value("${chronos.jwt.audience}") String audience,
            @Value("${chronos.jwt.lifetime-seconds:28800}") long lifetime,
            AuthSessionService sessions, Clock clock) {
        if (lifetime < 60 || lifetime > 86400) throw new IllegalArgumentException("JWT lifetime must be 60–86400 seconds");
        this.users=users; this.passwords=passwords; this.key=key; this.issuer=issuer; this.audience=audience;
        this.lifetime=lifetime; this.sessions=sessions; this.clock=clock;
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = ResponseStatusException.class)
    public String login(String email, String password) {
        User user = users.findByEmailForUpdate(email.trim()).orElse(null);
        Instant now = clock.instant();
        boolean matches = passwords.matches(password, user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash());
        if (user == null || !"ACTIVE".equals(user.getAccountStatus()) || !matches) {
            if (user != null && !matches && !user.isAdminLocked() && (user.getLockedUntil() == null || !now.isBefore(user.getLockedUntil()))) {
                user.setFailedLoginCount(user.getFailedLoginCount() + 1);
                user.setLastFailedAttempt(now);
                if (user.getFailedLoginCount() >= 5) {
                    user.setLockedUntil(now.plusSeconds(900));
                    user.setLockedAt(now);
                    user.setLockoutCycles(user.getLockoutCycles() + 1);
                    user.setFailedLoginCount(0);
                }
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to sign in. Check your credentials or try again later.");
        }
        if (user.isAdminLocked())
            throw new ResponseStatusException(HttpStatus.LOCKED, "Your account is locked. Contact an administrator.");
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to sign in. Check your credentials or try again later.");
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        try {
            var session = sessions.create(user.getId());
            SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                    .subject(user.getEntraId()).claim("employee_id", user.getId())
                    .claim("credential_version", user.getCredentialVersion())
                    .claim("preferred_username", user.getEmail()).issuer(issuer).audience(audience)
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(Math.min(lifetime, 28800))))
                    .jwtID(session.id).build());
            jwt.sign(new MACSigner(key.getEncoded()));
            return jwt.serialize();
        } catch (JOSEException ex) { throw new IllegalStateException("Unable to sign authentication token"); }
    }
}
