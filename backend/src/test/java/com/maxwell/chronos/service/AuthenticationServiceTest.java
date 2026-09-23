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
    AuthenticationService service=new AuthenticationService(users,passwords,key,"chronos","web",3600);
    JwtDecoder decoder=new JwtConfig().jwtDecoder(key,"chronos","web");
    User user;
    @BeforeEach void setup() {
        user=User.builder().id(42L).email("alice@example.com").entraId("employee-subject").isActive(true)
                .passwordHash(passwords.encode("StrongPassword123")).build();
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
    }
    @Test void successfulLoginIssuesSignedExpiringJwtIdentifyingEmployee() {
        Jwt jwt=decoder.decode(service.login(user.getEmail(),"StrongPassword123"));
        assertEquals("employee-subject",jwt.getSubject()); assertEquals(42L,((Number)jwt.getClaim("employee_id")).longValue());
        assertEquals(user.getEmail(),jwt.getClaimAsString("preferred_username"));
        assertEquals(3600,jwt.getExpiresAt().getEpochSecond()-jwt.getIssuedAt().getEpochSecond());
        assertFalse(jwt.getClaims().containsKey("passwordHash")); assertFalse(jwt.getClaims().containsKey("role"));
    }
    @Test void incorrectPasswordIsRejected() { reject("wrong"); }
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
        assertEquals(401,ex.getStatusCode().value()); assertEquals("Invalid email or password",ex.getReason());
    }
}
