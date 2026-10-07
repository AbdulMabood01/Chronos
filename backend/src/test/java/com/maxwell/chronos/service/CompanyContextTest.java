package com.maxwell.chronos.service;

import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompanyContextTest {
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final CompanyAccessService access = mock(CompanyAccessService.class);
    final CompanyManagementService service = new CompanyManagementService(db, access, mock(UserRepository.class),
            mock(InvitationDeliveryService.class), mock(AuditService.class), mock(OnboardingService.class), mock(PasswordEncoder.class));

    @Test void memberContextListsOnlyActiveMemberships() {
        var memberships = List.<Map<String,Object>>of(Map.of("id", 12L, "name", "Company A"));
        when(db.queryForList(contains("m.status='ACTIVE'"), eq(5L))).thenReturn(memberships);
        var context = service.companyContext(5L);
        assertFalse(context.platformAdmin());
        assertEquals(memberships, context.memberships());
        assertEquals(memberships, context.companies());
        verify(db, never()).queryForList(startsWith("SELECT id, name"));
    }

    @Test void noMembershipDoesNotPickAnArbitraryCompany() {
        when(db.queryForList(anyString(), eq(5L))).thenReturn(List.of());
        assertTrue(service.companyContext(5L).companies().isEmpty());
    }

    @Test void platformCatalogIsSeparateFromMemberships() {
        when(access.hasPlatformRole(1L, "PLATFORM_ADMIN")).thenReturn(true);
        when(db.queryForList(contains("company_memberships"), eq(1L))).thenReturn(List.of());
        when(db.queryForList(startsWith("SELECT id, name"))).thenReturn(List.of(Map.of("id", 12L)));
        var context = service.companyContext(1L);
        assertTrue(context.platformAdmin());
        assertTrue(context.memberships().isEmpty());
        assertEquals(1, context.companies().size());
    }

    @Test void selectionRequiresCurrentActiveMembership() {
        var company = Map.<String,Object>of("id", 12L, "name", "Company A");
        when(db.queryForList(contains("m.status='ACTIVE'"), eq(12L), eq(5L))).thenReturn(List.of(company));
        assertEquals(company.get("id"), service.validateCompanyContext(12L, 5L).get("id"));
        verify(access).companyPermissions(12L, 5L);
        when(db.queryForList(contains("m.status='ACTIVE'"), eq(12L), eq(5L))).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> service.validateCompanyContext(12L, 5L));
    }

    @Test void platformSelectionReturnsMetadataWithoutGrantingMembership() {
        when(access.hasPlatformRole(1L, "PLATFORM_ADMIN")).thenReturn(true);
        when(db.queryForList(startsWith("SELECT id, name"), eq(12L))).thenReturn(List.of(Map.of("id", 12L)));
        assertEquals(12L, service.validateCompanyContext(12L, 1L).get("id"));
        verify(db, never()).update(anyString());
    }
}
