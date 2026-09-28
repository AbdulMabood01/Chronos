package com.maxwell.chronos.service;

import com.maxwell.chronos.config.JwtConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {
    UserRepository users=mock(UserRepository.class);
    BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    SecretKeySpec key=new SecretKeySpec(new byte[32],"HmacSHA256");
    AuthSessionService sessions=mock(AuthSessionService.class);
    static class MutableClock extends java.time.Clock {
        Instant now = Instant.now();
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    MutableClock clock = new MutableClock();
    AuthenticationService service=new AuthenticationService(users,passwords,key,"chronos","web",3600,sessions,clock);
    JwtDecoder decoder=new JwtConfig().jwtDecoder(key,"chronos","web");
    User user;
    @BeforeEach void setup() {
        user=User.builder().id(42L).email("alice@example.com").entraId("employee-subject").isActive(true)
                .passwordHash(passwords.encode("StrongPassword123")).build();
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        var session = new com.maxwell.chronos.domain.AuthSession();
        session.id = java.util.UUID.randomUUID().toString();
        when(sessions.create(user.getId())).thenReturn(session);
    }
    @Test void successfulLoginIssuesSignedExpiringJwtIdentifyingEmployee() {
        Jwt jwt=decoder.decode(service.login(user.getEmail(),"StrongPassword123"));
        assertEquals("employee-subject",jwt.getSubject()); assertEquals(42L,((Number)jwt.getClaim("employee_id")).longValue());
        assertEquals(user.getEmail(),jwt.getClaimAsString("preferred_username"));
        assertEquals(3600,jwt.getExpiresAt().getEpochSecond()-jwt.getIssuedAt().getEpochSecond());
        assertFalse(jwt.getClaims().containsKey("passwordHash")); assertFalse(jwt.getClaims().containsKey("role"));
    }
    @Test void incorrectPasswordIsRejected() { reject("wrong"); }
    @Test void adminLockIsExplainedOnlyAfterCorrectPassword() {
        user.setAdminLocked(true);
        reject("wrong");
        var error = assertThrows(ResponseStatusException.class,
                () -> service.login(user.getEmail(), "StrongPassword123"));
        assertEquals(423, error.getStatusCode().value());
        assertEquals("Your account is locked. Contact an administrator.", error.getReason());
        verify(sessions, never()).create(anyLong());
    }
    @Test void fiveFailuresLockAccountAndSuccessfulLoginAfterExpiryResetsCounter() {
        for (int i = 0; i < 5; i++) reject("wrong");
        assertEquals(1, user.getLockoutCycles());
        assertEquals(clock.now.plusSeconds(900), user.getLockedUntil());
        clock.now = clock.now.plusSeconds(899);
        reject("StrongPassword123");
        clock.now = clock.now.plusSeconds(1);
        service.login(user.getEmail(), "StrongPassword123");
        assertEquals(0, user.getFailedLoginCount());
        assertNull(user.getLockedUntil());
    }
    @Test void successfulLoginResetsEarlierFailures() {
        reject("wrong"); reject("wrong");
        service.login(user.getEmail(), "StrongPassword123");
        assertEquals(0, user.getFailedLoginCount());
    }
    @Test void invitedEmployeeIsRejected() { user.setPasswordHash(null); reject("StrongPassword123"); }
    @Test void inactiveEmployeeIsRejected() { user.setIsActive(false); reject("StrongPassword123"); }
    @Test void unknownEmailDoesNotCreateAnAccount() {
        assertThrows(ResponseStatusException.class,()->service.login("unknown@example.com","StrongPassword123"));
        verify(users,never()).save(any()); verify(users,never()).saveAndFlush(any());
    }
    @Test void expiredJwtIsRejected() throws Exception {
        SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),new JWTClaimsSet.Builder().subject("employee-subject")
                .issuer("chronos").audience("web").issueTime(Date.from(Instant.now().minusSeconds(120)))
                .expirationTime(Date.from(Instant.now().minusSeconds(1))).build());
        jwt.sign(new MACSigner(key.getEncoded()));
        assertThrows(JwtException.class,()->decoder.decode(jwt.serialize()));
    }
    @Test void wrongSigningKeyIsRejected() {
        var otherKey=new SecretKeySpec(new byte[64],"HmacSHA256");
        // Nonzero key is needed: HMAC pads zero keys to its block length.
        byte[] bytes=otherKey.getEncoded(); Arrays.fill(bytes,(byte)1);
        JwtDecoder other=new JwtConfig().jwtDecoder(new SecretKeySpec(bytes,"HmacSHA256"),"chronos","web");
        assertThrows(JwtException.class,()->other.decode(service.login(user.getEmail(),"StrongPassword123")));
    }
    @Test void shortSecretIsRejected() { assertThrows(IllegalStateException.class,()->new JwtConfig().jwtKey(Base64.getEncoder().encodeToString(new byte[16]))); }
    void reject(String password) {
        var ex=assertThrows(ResponseStatusException.class,()->service.login(user.getEmail(),password));
        assertEquals(401,ex.getStatusCode().value()); assertEquals("Unable to sign in. Check your credentials or try again later.",ex.getReason());
    }
}
