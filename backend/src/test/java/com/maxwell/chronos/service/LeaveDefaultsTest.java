package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LeaveDefaultsTest {
    @Test void annualPolicyPreservesOverridesAndExtraGrants() {
        var settings = mock(SystemSettingRepository.class);
        var users = mock(UserRepository.class);
        var allowances = mock(LeaveAllowanceRepository.class);
        var policies = mock(LeavePolicyYearRepository.class);
        var service = new SystemSettingsService(settings, mock(AuditService.class), users, allowances, policies);
        var admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var systemAdmin = User.builder().id(3L).role(UserRole.ADMIN).build();
        when(users.findAll()).thenReturn(List.of(employee, systemAdmin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        var existing = new LeaveAllowance(); existing.setExtraVacationDays(BigDecimal.TEN);
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(existing));
        for (var entry : Map.of("vacation_days_per_year", "15", "sick_days_per_year", "5", "bereavement_days_per_year", "3").entrySet())
            when(settings.findBySettingKey(entry.getKey())).thenReturn(Optional.of(SystemSetting.builder().settingKey(entry.getKey()).settingValue(entry.getValue()).build()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.applyLeaveDefaults(2026, true, employee));
        assertThrows(IllegalArgumentException.class, () -> service.applyLeaveDefaults(2026, false, admin));
        assertEquals(0, service.applyLeaveDefaults(2026, true, admin));
        assertEquals(BigDecimal.ZERO, existing.getVacationDays());
        assertEquals(BigDecimal.TEN, existing.getExtraVacationDays());
        verify(allowances, never()).save(existing);
        verify(policies).save(any(LeavePolicyYear.class));
        verify(users, never()).findForUpdate(3L);
    }
    @Test void rejectsInvalidPolicyValues() {
        var service = new SystemSettingsService(mock(SystemSettingRepository.class), mock(AuditService.class), mock(UserRepository.class), mock(LeaveAllowanceRepository.class), mock(LeavePolicyYearRepository.class));
        for (String value : List.of("-1", "367", "NaN", "1.001", ""))
            assertThrows(IllegalArgumentException.class, () -> service.updateSetting("sick_days_per_year", value, 1L));
    }

    @Test void applyingPolicyUpdatesManagedAllowancesWithoutErasingExtraDays() {
        var settings = mock(SystemSettingRepository.class);
        var users = mock(UserRepository.class);
        var allowances = mock(LeaveAllowanceRepository.class);
        var policies = mock(LeavePolicyYearRepository.class);
        var service = new SystemSettingsService(settings, mock(AuditService.class), users, allowances, policies);
        var admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var managed = new LeaveAllowance(); managed.setUserId(1L); managed.setYear(2026);
        managed.setSource("POLICY"); managed.setExtraVacationDays(BigDecimal.valueOf(4));
        when(users.findAll()).thenReturn(List.of(employee));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(managed));
        for (var entry : Map.of("vacation_days_per_year", "15", "sick_days_per_year", "5", "bereavement_days_per_year", "3").entrySet())
            when(settings.findBySettingKey(entry.getKey())).thenReturn(Optional.of(SystemSetting.builder().settingKey(entry.getKey()).settingValue(entry.getValue()).build()));

        assertEquals(1, service.applyLeaveDefaults(2026, true, admin));
        assertEquals(BigDecimal.valueOf(15), managed.getVacationDays());
        assertEquals(BigDecimal.valueOf(4), managed.getExtraVacationDays());
        assertEquals("POLICY", managed.getSource());
    }
}
