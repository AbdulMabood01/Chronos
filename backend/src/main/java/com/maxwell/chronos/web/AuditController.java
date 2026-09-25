package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.AuditLogDTO;
import com.maxwell.chronos.service.AuditService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AuditLogDTO>> getAuditLogs(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<AuditLogDTO> auditLogs = auditService.getAuditLogs();
        return ResponseEntity.ok(auditLogs);
    }
}
