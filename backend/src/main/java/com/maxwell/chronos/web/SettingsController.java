package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.SystemSettingDTO;
import com.maxwell.chronos.service.SystemSettingsService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {
    private final SystemSettingsService systemSettingsService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SystemSettingDTO>> getAllSettings(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isSuperAdmin() && !user.isAdmin())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(systemSettingsService.getAllSettings());
    }

    @GetMapping("/{key}")
    public ResponseEntity<SystemSettingDTO> getSetting(@PathVariable String key, @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isSuperAdmin() && !user.isAdmin())) {
            return ResponseEntity.status(403).build();
        }
        SystemSettingDTO setting = systemSettingsService.getSetting(key);
        if (setting == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(setting);
    }

    @PutMapping("/{key}")
    public ResponseEntity<SystemSettingDTO> updateSetting(@PathVariable String key,
                                                           @RequestBody Map<String, String> body,
                                                           @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isSuperAdmin() && !user.isAdmin())) {
            return ResponseEntity.status(403).build();
        }
        SystemSettingDTO updated = systemSettingsService.updateSetting(key, body.get("value"), user.getId());
        return ResponseEntity.ok(updated);
    }
}
