package com.maxwell.chronos.config;

import com.maxwell.chronos.security.DevJwtKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfig {
    @Bean
    @Profile("dev")
    JwtDecoder developmentDecoder(@Value("${chronos.jwtSigningKey}") String key) {
        return NimbusJwtDecoder.withSecretKey(DevJwtKeys.deriveKey(key)).build();
    }

    @Bean
    @Profile("!dev")
    JwtDecoder entraDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                           @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
                           @Value("${chronos.jwtAudience}") String audience) {
        if (issuer.isBlank() || issuer.contains("/common/") || audience.isBlank()) {
            throw new IllegalStateException("Configure a tenant-specific Entra issuer and API audience");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }
}
