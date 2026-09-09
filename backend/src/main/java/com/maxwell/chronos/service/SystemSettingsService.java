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
