package com.maxwell.chronos.web;

import com.maxwell.chronos.service.PasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {
    private final PasswordService passwords;
    public record ForgotRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record TokenRequest(@NotBlank @Size(max = 43) String token) {}
    public record ResetRequest(@NotBlank @Size(max = 43) String token,
            @NotBlank @Size(max = 72) String newPassword, @NotBlank @Size(max = 72) String confirmation) {}
    public record ChangeRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(max = 72) String newPassword, @NotBlank @Size(max = 72) String confirmation) {}

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest request) {
        passwords.forgot(request.email());
        return Map.of("message", "If an active account matches that email, you will receive a reset link shortly. Check your inbox and spam folder.");
    }
    @PostMapping("/reset-password/validate")
    public Map<String, String> validate(@Valid @RequestBody TokenRequest request) {
        passwords.validate(request.token());
        return Map.of("message", "Reset link is valid.");
    }
    @PostMapping("/reset-password")
    public Map<String, String> reset(@Valid @RequestBody ResetRequest request) {
        passwords.reset(request.token(), request.newPassword(), request.confirmation());
        return Map.of("message", "Password reset. Sign in with your new password.");
    }
    @PostMapping("/change-password")
    public Map<String, String> change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeRequest request) {
        passwords.change(jwt.getSubject(), request.currentPassword(), request.newPassword(), request.confirmation());
        return Map.of("message", "Password changed. Sign in with your new password.");
    }
}
