package com.maxwell.chronos.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Local-development-only JWT signing key derivation (HS256), shared by the decoder and the dev token issuer.
public final class DevJwtKeys {
    private DevJwtKeys() {
    }

    public static SecretKey deriveKey(String rawSigningKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawSigningKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hashed, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
