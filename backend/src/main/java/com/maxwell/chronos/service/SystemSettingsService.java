package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.SystemSetting;
import com.maxwell.chronos.dto.SystemSettingDTO;
import com.maxwell.chronos.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class SystemSettingsService {
    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;
    private final com.maxwell.chronos.repository.UserRepository users;
    private final com.maxwell.chronos.repository.LeaveAllowanceRepository allowances;

    private java.math.BigDecimal leaveDays(String value) {
        try {
            var days = new java.math.BigDecimal(value);
            if (days.signum() < 0 || days.compareTo(java.math.BigDecimal.valueOf(366)) > 0 || days.scale() > 2)
                throw new IllegalArgumentException();
            return days;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Leave days must be between 0 and 366 with at most two decimal places");
        }
    }

    public int applyLeaveDefaults(int year, boolean confirmed, com.maxwell.chronos.domain.User requester) {
        if (requester == null || (!requester.isProjectAdmin() && !requester.isAdmin()))
            throw new org.springframework.security.access.AccessDeniedException("Admin or Project Admin permission required");
        if (!confirmed || year < 1900 || year > 9998) throw new IllegalArgumentException("Confirm a valid leave year");
        var vacation = leaveDays(getSetting("vacation_days_per_year").getValue());
        var sick = leaveDays(getSetting("sick_days_per_year").getValue());
        var bereavement = leaveDays(getSetting("bereavement_days_per_year").getValue());
        int count = 0;
        for (var employee : users.findAll().stream().sorted(java.util.Comparator.comparing(com.maxwell.chronos.domain.User::getId)).toList()) {
            if (employee.isAdmin()) continue;
            users.findForUpdate(employee.getId()).orElseThrow();
            var allowance = allowances.findByUserIdAndYear(employee.getId(), year)
                    .orElseGet(com.maxwell.chronos.domain.LeaveAllowance::new);
            allowance.setUserId(employee.getId()); allowance.setYear(year);
            allowance.setVacationDays(vacation); allowance.setSickDays(sick); allowance.setBereavementDays(bereavement);
            allowance.setExtraVacationDays(java.math.BigDecimal.ZERO); allowance.setExtraSickDays(java.math.BigDecimal.ZERO);
            allowances.save(allowance); count++;
        }
        auditService.logAction(requester.getId(), "SETTINGS_UPDATED", "LeaveAllowance", null,
                "Applied leave defaults for " + year + " to " + count + " employees; vacation=" + vacation + "; sick=" + sick + "; bereavement=" + bereavement);
        return count;
    }

    public List<SystemSettingDTO> getAllSettings() {
        return systemSettingRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SystemSettingDTO getSetting(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .map(this::toDTO)
                .orElse(null);
    }

    public SystemSettingDTO updateSetting(String key, String value, Long updatingUserId) {
        if ("default_hourly_rate".equals(key)) throw new IllegalArgumentException("User hourly rates are no longer supported");
        if (java.util.Set.of("vacation_days_per_year", "sick_days_per_year", "bereavement_days_per_year").contains(key)) leaveDays(value);
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        SystemSetting saved = systemSettingRepository.save(setting);

        auditService.logAction(updatingUserId, "SETTINGS_UPDATED", "SystemSetting", saved.getId(),
                "Key: " + key + ", Value: " + value);

        return toDTO(saved);
    }

    private SystemSettingDTO toDTO(SystemSetting setting) {
        return SystemSettingDTO.builder()
                .key(setting.getSettingKey())
                .value(setting.getSettingValue())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}
