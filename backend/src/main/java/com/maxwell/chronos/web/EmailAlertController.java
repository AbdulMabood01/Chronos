package com.maxwell.chronos.web;

import com.maxwell.chronos.service.EmailAlertService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications/email-preferences")
@RequiredArgsConstructor
public class EmailAlertController {
    private final EmailAlertService alerts;
    private final UserService users;

    private Long userId(Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) throw new AccessDeniedException("Access denied");
        return user.getId();
    }

    @GetMapping
    public EmailAlertService.Preferences get(@AuthenticationPrincipal Jwt jwt) {
        return alerts.preferences(userId(jwt));
    }

    @PutMapping
    public EmailAlertService.Preferences save(@AuthenticationPrincipal Jwt jwt, @RequestBody EmailAlertService.Preferences preferences) {
        return alerts.save(userId(jwt), preferences);
    }
}
