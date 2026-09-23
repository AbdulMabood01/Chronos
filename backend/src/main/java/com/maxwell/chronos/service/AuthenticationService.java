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
import java.util.*;

@Service
public class AuthenticationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final String issuer, audience, dummyHash;
    private final long lifetime;
    public AuthenticationService(UserRepository users, PasswordEncoder passwords, SecretKey key,
            @Value("${chronos.jwt.issuer}") String issuer, @Value("${chronos.jwt.audience}") String audience,
            @Value("${chronos.jwt.lifetime-seconds:3600}") long lifetime) {
        if (lifetime < 60 || lifetime > 86400) throw new IllegalArgumentException("JWT lifetime must be 60–86400 seconds");
        this.users=users; this.passwords=passwords; this.key=key; this.issuer=issuer; this.audience=audience;
        this.lifetime=lifetime; this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public String login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        boolean matches = passwords.matches(password, user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null || !"ACTIVE".equals(user.getAccountStatus()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        try {
            Instant now=Instant.now();
            SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                    .subject(user.getEntraId()).claim("employee_id", user.getId())
                    .claim("preferred_username", user.getEmail()).issuer(issuer).audience(audience)
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(lifetime)))
                    .jwtID(UUID.randomUUID().toString()).build());
            jwt.sign(new MACSigner(key.getEncoded()));
            return jwt.serialize();
        } catch (JOSEException ex) { throw new IllegalStateException("Unable to sign authentication token"); }
    }
}
