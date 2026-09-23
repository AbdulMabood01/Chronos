package com.maxwell.chronos.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
@Configuration
public class JwtConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public SecretKey jwtKey(@Value("${chronos.jwtSigningKey}") String value) {
        byte[] bytes=Base64.getDecoder().decode(value);
        if(bytes.length<32) throw new IllegalStateException("JWT_SIGNING_KEY must contain at least 32 random bytes, Base64 encoded");
        return new SecretKeySpec(bytes,"HmacSHA256");
    }
    @Bean public JwtDecoder jwtDecoder(SecretKey key, @Value("${chronos.jwt.issuer}") String issuer,
            @Value("${chronos.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).build();
        OAuth2TokenValidator<Jwt> audiences=token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ZERO),new JwtIssuerValidator(issuer),audiences));
        return decoder;
    }
}
