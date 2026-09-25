package com.maxwell.chronos.config;

import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.OnboardingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class InitialAdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    @Bean ApplicationRunner bootstrapAdmin(UserRepository users, OnboardingService onboarding,
            TransactionTemplate transaction, JdbcTemplate jdbc,
            @Value("${chronos.bootstrap.email:}") String email) {
        return args -> transaction.executeWithoutResult(status -> {
            // Serialize concurrent bootstrap processes across application instances.
            jdbc.execute("SELECT pg_advisory_xact_lock(823471901)");
            if(users.existsByRole(UserRole.ADMIN)) {
                log.info("Admin already exists; skipping bootstrap");
                return;
            }

            String normalizedEmail=email.trim().toLowerCase(java.util.Locale.ROOT);
            if(normalizedEmail.isBlank()) {
                log.warn("No Admin exists and ADMIN_EMAIL is not set; skipping bootstrap");
                return;
            }
            if(!normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || normalizedEmail.length()>255)
                throw new IllegalStateException("ADMIN_EMAIL must contain a valid email address");
            if(users.findByEmailIgnoreCase(normalizedEmail).isPresent())
                throw new IllegalStateException("ADMIN_EMAIL belongs to an existing non-Admin account; bootstrap will not overwrite it");

            var user=onboarding.create("System", "Admin", normalizedEmail);
            user.setRole(UserRole.ADMIN); users.saveAndFlush(user);
            onboarding.invite(user.getId());
            log.info("Created the initial Admin and sent an account setup invitation to {}", normalizedEmail);
        });
    }
}
