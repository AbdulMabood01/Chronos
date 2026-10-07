package com.maxwell.chronos.e2e;

import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/e2e")
@Profile("e2e")
@RequiredArgsConstructor
public class E2eFixtureController {
    private final E2eFixtures fixtures;
    private final UserService users;

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@AuthenticationPrincipal Jwt jwt) {
        var requester = jwt == null ? null : users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null || !E2eFixtures.ADMIN_EMAIL.equals(requester.getEmail())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(fixtures.reset());
    }

    @PostMapping("/invitation-token/{employeeId}")
    public ResponseEntity<Map<String, String>> invitationToken(@PathVariable long employeeId,
                                                                @AuthenticationPrincipal Jwt jwt) {
        var requester = jwt == null ? null : users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null || !E2eFixtures.ADMIN_EMAIL.equals(requester.getEmail())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of("token", fixtures.issueInvitationToken(employeeId)));
    }

    @PostMapping("/password-reset-token/{employeeId}")
    public ResponseEntity<Map<String, String>> passwordResetToken(@PathVariable long employeeId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        var requester = jwt == null ? null : users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null || !E2eFixtures.ADMIN_EMAIL.equals(requester.getEmail())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of("token", fixtures.issuePasswordResetToken(employeeId)));
    }

    @PostMapping("/company-invitation-token/{invitationId}")
    public ResponseEntity<Map<String,String>> companyInvitationToken(@PathVariable long invitationId,@AuthenticationPrincipal Jwt jwt) {
        var requester=jwt==null?null:users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if(requester==null||!E2eFixtures.ADMIN_EMAIL.equals(requester.getEmail())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of("token",fixtures.issueCompanyInvitationToken(invitationId)));
    }

    @PostMapping("/company-invitations/{invitationId}/expire")
    public ResponseEntity<Void> expireCompanyInvitation(@PathVariable long invitationId,@AuthenticationPrincipal Jwt jwt) {
        var requester=jwt==null?null:users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if(requester==null||!E2eFixtures.ADMIN_EMAIL.equals(requester.getEmail())) return ResponseEntity.status(403).build();
        fixtures.expireCompanyInvitation(invitationId);
        return ResponseEntity.ok().build();
    }
}
