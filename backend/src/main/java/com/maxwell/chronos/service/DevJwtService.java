package com.maxwell.chronos.service;

import com.maxwell.chronos.security.DevJwtKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

// Issues locally-signed JWTs for development/testing, bypassing real Microsoft Entra ID login.
@Service
public class DevJwtService {
    private final SecretKey secretKey;

    public DevJwtService(@Value("${chronos.jwtSigningKey}") String signingKey) {
        this.secretKey = DevJwtKeys.deriveKey(signingKey);
    }

    public String generateToken(String subject, String email, String firstName, String lastName) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("preferred_username", email)
                    .claim("given_name", firstName)
                    .claim("family_name", lastName)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(24 * 60 * 60)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(secretKey.getEncoded()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate dev token", e);
        }
    }
}
