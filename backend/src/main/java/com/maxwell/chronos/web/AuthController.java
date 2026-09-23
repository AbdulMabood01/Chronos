package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.AuthResponse;

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
                .joiningDate(user.getJoiningDate())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(user.isSuperAdmin() ? null : user.getSsnLast4())
                .profileImageUrl(user.getProfileImageUrl())
                .phoneNumber(user.getPhoneNumber())
                .personalEmail(user.getPersonalEmail())
                .timezone(user.getTimezone())
                .addressLine1(user.getAddressLine1())
                .addressLine2(user.getAddressLine2())
                .city(user.getCity())
                .stateProvince(user.getStateProvince())
                .postalCode(user.getPostalCode())
                .country(user.getCountry())
                .bloodGroup(user.getBloodGroup())
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactRelationship(user.getEmergencyContactRelationship())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .emergencyContactEmail(user.getEmergencyContactEmail())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .role(user.getRole().toString())
                .canReviewProjects(projectService.canReviewProjects(user.getId()))
                .canManageProjects(projectService.canManageProjects(user.getId()))
                .isActive(user.getIsActive())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !"ACTIVE".equals(user.getAccountStatus())) {
            return ResponseEntity.status(403).body(null);
        }

        return ResponseEntity.ok(toAuthResponse(user));
    }
}
