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
    @Test void bulkDefaultsReplaceAllowancesAndExtrasAndAreRepeatable() {
        var settings = mock(SystemSettingRepository.class);
        var users = mock(UserRepository.class);
        var allowances = mock(LeaveAllowanceRepository.class);
        var service = new SystemSettingsService(settings, mock(AuditService.class), users, allowances);
        var admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var superAdmin = User.builder().id(3L).role(UserRole.SUPER_ADMIN).build();
        when(users.findAll()).thenReturn(List.of(employee, superAdmin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        var existing = new LeaveAllowance(); existing.setExtraVacationDays(BigDecimal.TEN);
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(existing));
        for (var entry : Map.of("vacation_days_per_year", "15", "sick_days_per_year", "5", "bereavement_days_per_year", "3").entrySet())
            when(settings.findBySettingKey(entry.getKey())).thenReturn(Optional.of(SystemSetting.builder().settingKey(entry.getKey()).settingValue(entry.getValue()).build()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.applyLeaveDefaults(2026, true, employee));
        assertThrows(IllegalArgumentException.class, () -> service.applyLeaveDefaults(2026, false, admin));
        assertEquals(1, service.applyLeaveDefaults(2026, true, admin));
        assertEquals(1, service.applyLeaveDefaults(2026, true, admin));
        assertEquals(new BigDecimal("15"), existing.getVacationDays());
        assertEquals(new BigDecimal("5"), existing.getSickDays());
        assertEquals(new BigDecimal("3"), existing.getBereavementDays());
        assertEquals(BigDecimal.ZERO, existing.getExtraVacationDays());
        verify(users, never()).findForUpdate(3L);
    }
    @Test void rejectsInvalidPolicyValues() {
        var service = new SystemSettingsService(mock(SystemSettingRepository.class), mock(AuditService.class), mock(UserRepository.class), mock(LeaveAllowanceRepository.class));
        for (String value : List.of("-1", "367", "NaN", "1.001", ""))
            assertThrows(IllegalArgumentException.class, () -> service.updateSetting("sick_days_per_year", value, 1L));
    }
}
