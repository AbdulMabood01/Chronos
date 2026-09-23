package com.maxwell.chronos.config;

import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.OnboardingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(name="chronos.bootstrap.enabled",havingValue="true")
public class InitialSuperAdminBootstrap {
    @Bean ApplicationRunner bootstrapAdmin(UserRepository users, OnboardingService onboarding,
            TransactionTemplate transaction, JdbcTemplate jdbc,
            @Value("${chronos.bootstrap.email:}") String email,
            @Value("${chronos.bootstrap.first-name:}") String firstName,
            @Value("${chronos.bootstrap.last-name:}") String lastName) {
        return args -> transaction.executeWithoutResult(status -> {
            // Serialize concurrent bootstrap processes across application instances.
            jdbc.execute("SELECT pg_advisory_xact_lock(823471901)");
            if(email.isBlank() || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>255
                    || firstName.isBlank() || lastName.isBlank() || firstName.length()>100 || lastName.length()>100)
                throw new IllegalStateException("Set valid bootstrap email, first name and last name");
            var existing=users.findByEmailIgnoreCase(email.trim());
            if(existing.isPresent()) {
                var user=existing.get();
                if(!user.isSuperAdmin()) throw new IllegalStateException("Bootstrap cannot promote an existing employee");
                if("INVITED".equals(user.getAccountStatus())) onboarding.invite(user.getId());
                return;
            }
            if(users.existsByRole(UserRole.SUPER_ADMIN)) throw new IllegalStateException("A Super Admin already exists; use employee management");
            var user=onboarding.create(firstName,lastName,email);
            user.setRole(UserRole.SUPER_ADMIN); users.saveAndFlush(user);
            onboarding.invite(user.getId());
        });
    }
}
