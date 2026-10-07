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
    private final com.maxwell.chronos.service.AuthSessionService sessions;
    private final com.maxwell.chronos.service.CompanyAccessService companyAccess;

    @PostMapping("/activity")
    public ResponseEntity<Map<String, Object>> activity(@AuthenticationPrincipal Jwt jwt) {
        Long id = ((Number) jwt.getClaim("employee_id")).longValue();
        var expires = sessions.touch(jwt.getId(), id);
        return expires == null ? ResponseEntity.status(401).build()
                : ResponseEntity.ok(Map.of("expiresAt", expires.toString()));
    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal Jwt jwt) { sessions.revoke(jwt.getId()); }

    private AuthResponse toAuthResponse(com.maxwell.chronos.domain.User user) {
        return AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(companyAccess.hasPlatformRole(user.getId(),"PLATFORM_ADMIN") ? null : user.getSsnLast4())
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
                .bloodGroup(user.getBloodGroup()).gender(user.getGender()).race(user.getRace()).ethnicity(user.getEthnicity()).joiningDate(user.getJoiningDate()).profileDetailsSubmitted(Boolean.TRUE.equals(user.getProfileDetailsSubmitted()))
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactRelationship(user.getEmergencyContactRelationship())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .emergencyContactEmail(user.getEmergencyContactEmail())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .platformAdmin(companyAccess.hasPlatformRole(user.getId(),"PLATFORM_ADMIN"))
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
