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
    private final com.maxwell.chronos.repository.LeavePolicyYearRepository policies;

    public record LeaveDefaultsPreview(int year, int newAllowances, int policyAllowances, int overrides,
            java.math.BigDecimal vacationDays, java.math.BigDecimal sickDays, java.math.BigDecimal bereavementDays) {}

    public LeaveDefaultsPreview previewLeaveDefaults(int year, com.maxwell.chronos.domain.User requester) {
        if (requester == null || !requester.isAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only Admin can manage leave policy");
        if (year < 1900 || year > 9998) throw new IllegalArgumentException("Choose a valid leave year");
        var vacation = leaveDays(getSetting("vacation_days_per_year").getValue());
        var sick = leaveDays(getSetting("sick_days_per_year").getValue());
        var bereavement = leaveDays(getSetting("bereavement_days_per_year").getValue());
        int missing = 0, managed = 0, overrides = 0;
        for (var employee : users.findAll()) {
            if (employee.isAdmin() || Boolean.FALSE.equals(employee.getIsActive()) || employee.getJoiningDate() != null && employee.getJoiningDate().getYear() > year) continue;
            var allowance = allowances.findByUserIdAndYear(employee.getId(), year);
            if (allowance.isEmpty()) missing++;
            else if ("POLICY".equals(allowance.get().getSource())) managed++;
            else overrides++;
        }
        return new LeaveDefaultsPreview(year, missing, managed, overrides, vacation, sick, bereavement);
    }

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
        return applyLeaveDefaults(year, confirmed, requester, null, null, null);
    }

    public int applyLeaveDefaults(int year, boolean confirmed, com.maxwell.chronos.domain.User requester,
            java.math.BigDecimal expectedVacation, java.math.BigDecimal expectedSick,
            java.math.BigDecimal expectedBereavement) {
        var preview = previewLeaveDefaults(year, requester);
        if (!confirmed || year < 1900 || year > 9998) throw new IllegalArgumentException("Confirm a valid leave year");
        if (expectedVacation != null && expectedSick != null && expectedBereavement != null
                && (preview.vacationDays().compareTo(expectedVacation) != 0
                    || preview.sickDays().compareTo(expectedSick) != 0
                    || preview.bereavementDays().compareTo(expectedBereavement) != 0))
            throw new IllegalArgumentException("Leave policy values changed. Preview the policy again before applying it");
        var policy = policies.findById(year).orElseGet(com.maxwell.chronos.domain.LeavePolicyYear::new);
        policy.setYear(year); policy.setVacationDays(preview.vacationDays()); policy.setSickDays(preview.sickDays());
        policy.setBereavementDays(preview.bereavementDays()); policies.save(policy);
        int count = 0;
        for (var employee : users.findAll().stream().sorted(java.util.Comparator.comparing(com.maxwell.chronos.domain.User::getId)).toList()) {
            if (employee.isAdmin() || Boolean.FALSE.equals(employee.getIsActive()) || employee.getJoiningDate() != null && employee.getJoiningDate().getYear() > year) continue;
            users.findForUpdate(employee.getId()).orElseThrow();
            var existing = allowances.findByUserIdAndYear(employee.getId(), year);
            if (existing.isPresent() && !"POLICY".equals(existing.get().getSource())) continue;
            var allowance = existing.orElseGet(com.maxwell.chronos.domain.LeaveAllowance::new);
            allowance.setUserId(employee.getId()); allowance.setYear(year);
            allowance.setVacationDays(preview.vacationDays()); allowance.setSickDays(preview.sickDays());
            allowance.setBereavementDays(preview.bereavementDays()); allowance.setSource("POLICY");
            allowances.save(allowance); count++;
        }
        auditService.logAction(requester.getId(), "SETTINGS_UPDATED", "LeaveAllowance", null,
                "Applied leave policy for " + year + " to " + count + " employees; preserved " + preview.overrides() + " overrides");
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
