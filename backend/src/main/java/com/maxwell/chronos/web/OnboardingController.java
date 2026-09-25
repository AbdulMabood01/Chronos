package com.maxwell.chronos.web;

import com.maxwell.chronos.service.*;
import com.maxwell.chronos.dto.UserDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboarding;
    private final AuthenticationService authentication;
    private final UserService users;
    private final EmployeeImportService employeeImport;

    @PostMapping(value = "/users/import", consumes = "multipart/form-data")
    public Map<String, Integer> importEmployees(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                               @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return Map.of("imported", employeeImport.importEmployees(file));
    }
    // Request classes deliberately have no generated toString: credentials must not appear in logs.
    public static class LoginRequest {
        @NotBlank @Email @Size(max=255) public String email;
        @NotBlank @Size(max=72) public String password;
    }
    public static class TokenRequest {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}", message="Invalid invitation") public String token;
    }
    public static class ActivationRequest extends TokenRequest {
        @NotBlank @Size(max=72) public String password;
    }
    public static class EmployeeRequest {
        @NotBlank @Size(max=100) public String firstName;
        @NotBlank @Size(max=100) public String lastName;
        @NotBlank @Email @Size(max=255) public String email;
    }
    @PostMapping("/auth/login")
    public Map<String,String> login(@Valid @RequestBody LoginRequest request) {
        return Map.of("token",authentication.login(request.email,request.password));
    }
    @PostMapping("/auth/invitations/validate")
    public OnboardingService.InvitationInfo validate(@Valid @RequestBody TokenRequest request) {
        return onboarding.validate(request.token);
    }
    @PostMapping("/auth/activate")
    public void activate(@Valid @RequestBody ActivationRequest request) {
        onboarding.activate(request.token,request.password);
    }
    @PostMapping("/users")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public UserDTO create(@Valid @RequestBody EmployeeRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return users.findById(onboarding.create(request.firstName,request.lastName,request.email).getId());
    }
    @PostMapping("/users/{id}/invitation")
    public void invite(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt); onboarding.invite(id);
    }
    @DeleteMapping("/users/{id}/invitation")
    public void revoke(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt); onboarding.revoke(id);
    }
    private void requireAdmin(Jwt jwt) {
        var requester=jwt==null ? null : users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if(requester==null || !requester.isAdmin()) throw new AccessDeniedException("Only Admin can manage onboarding");
    }
}
