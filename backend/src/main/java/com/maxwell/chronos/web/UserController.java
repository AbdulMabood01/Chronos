package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        var requester = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null || (!requester.isSuperAdmin() && !requester.getId().equals(id))) {
            return ResponseEntity.status(403).build();
        }
        UserDTO user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<List<com.maxwell.chronos.dto.EmployeeDirectoryDTO>> getAllActiveUsers() {
        var users = userService.getEmployeeDirectory();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserDTO> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        try {
            return ResponseEntity.ok(userService.updateOwnProfile(email, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserDTO>> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var currentUser = userService.findUserEntityByEmail(email);

        if (currentUser == null || !currentUser.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var currentUser = userService.findUserEntityByEmail(email);

        if (currentUser == null || !currentUser.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        userService.deactivateUser(id, currentUser.getId());
        UserDTO updatedUser = userService.findById(id);
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<UserDTO> reactivateUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var currentUser = userService.findUserEntityByEmail(email);

        if (currentUser == null || !currentUser.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        userService.reactivateUser(id, currentUser.getId());
        UserDTO updatedUser = userService.findById(id);
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserDTO> changeRole(@PathVariable Long id, @RequestParam UserRole role,
                                              @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var currentUser = userService.findUserEntityByEmail(email);

        if (currentUser == null || !currentUser.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            userService.changeRole(id, role, currentUser.getId());
        } catch (IllegalArgumentException e) {
            throw e;
        }
        UserDTO updatedUser = userService.findById(id);
        return ResponseEntity.ok(updatedUser);
    }
}
