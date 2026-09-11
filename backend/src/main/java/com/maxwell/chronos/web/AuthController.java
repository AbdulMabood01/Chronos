package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.AuthResponse;
import com.maxwell.chronos.service.DevJwtService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final com.maxwell.chronos.service.ProjectService projectService;

    private AuthResponse toAuthResponse(com.maxwell.chronos.domain.User user) {
        return AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .jobTitle(user.getJobTitle())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(user.isSuperAdmin() ? null : user.getSsnLast4())
                .profileImageUrl(user.getProfileImageUrl())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .role(user.getRole().toString())
                .canReviewProjects(projectService.canReviewProjects(user.getId()))
                .isActive(user.getIsActive())
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String entraId = jwt.getClaimAsString("sub");

        var user = userService.findOrCreateByEntraId(entraId, email, firstName, lastName);
        if (!user.getIsActive()) {
            return ResponseEntity.status(403).body(null);
        }

        return ResponseEntity.ok(toAuthResponse(user));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.getIsActive()) {
            return ResponseEntity.status(403).body(null);
        }

        return ResponseEntity.ok(toAuthResponse(user));
    }
}
