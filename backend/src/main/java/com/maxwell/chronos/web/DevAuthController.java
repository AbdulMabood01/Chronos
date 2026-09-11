package com.maxwell.chronos.web;
import com.maxwell.chronos.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@Profile("dev")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DevAuthController {
    private final UserService userService;
    private final DevJwtService devJwtService;
    // Local-development-only login: issues a locally-signed token for the given email, bypassing Entra ID.
    @PostMapping("/dev-login")
    public ResponseEntity<Map<String, String>> devLogin(@RequestParam String email,
                                                          @RequestParam(defaultValue = "Dev") String firstName,
                                                          @RequestParam(defaultValue = "User") String lastName) {
        var user = userService.findOrCreateByEmailForDev(email, firstName, lastName);
        if (!user.getIsActive()) {
            return ResponseEntity.status(403).build();
        }

        String token = devJwtService.generateToken(user.getEntraId(), user.getEmail(), user.getFirstName(), user.getLastName());
        return ResponseEntity.ok(Map.of("token", token));
    }

}
