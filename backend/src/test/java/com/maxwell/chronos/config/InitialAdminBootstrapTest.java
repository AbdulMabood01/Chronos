package com.maxwell.chronos.config;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.OnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InitialAdminBootstrapTest {
    private final UserRepository users=mock(UserRepository.class);
    private final OnboardingService onboarding=mock(OnboardingService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final TransactionTemplate transaction=mock(TransactionTemplate.class);
    private final InitialAdminBootstrap bootstrap=new InitialAdminBootstrap();

    private void run(String email) throws Exception {
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        bootstrap.bootstrapAdmin(users,onboarding,transaction,jdbc,email).run(null);
    }

    @Test void existingAdminIsLeftUntouched() throws Exception {
        when(users.existsByRole(UserRole.ADMIN)).thenReturn(true);
        run("replacement@example.com");
        verifyNoInteractions(onboarding);
        verify(users,never()).findByEmailIgnoreCase(anyString());
        verify(users,never()).saveAndFlush(any());
    }

    @Test void missingEmailSkipsBootstrapSafely() throws Exception {
        run("  ");
        verifyNoInteractions(onboarding);
        verify(users,never()).saveAndFlush(any());
    }

    @Test void createsAndInvitesInitialAdmin() throws Exception {
        User created=User.builder().id(42L).role(UserRole.EMPLOYEE).build();
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
        when(onboarding.create("System","Admin","admin@example.com")).thenReturn(created);

        run(" ADMIN@EXAMPLE.COM ");

        verify(users).saveAndFlush(created);
        verify(onboarding).invite(42L);
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.ADMIN,created.getRole());
    }

    @Test void existingEmailIsNeverPromotedOrOverwritten() {
        when(users.findByEmailIgnoreCase("employee@example.com")).thenReturn(Optional.of(new User()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->run("employee@example.com"));
        verifyNoInteractions(onboarding);
        verify(users,never()).saveAndFlush(any());
    }
}
