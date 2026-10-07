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
    private final com.maxwell.chronos.service.ProjectService projectService;
    private final com.maxwell.chronos.service.CompanyAccessService companyAccess;
    private final com.maxwell.chronos.service.LeaveBalanceService leaveBalanceService;

    @RequestMapping(value={"/{id}/lock","/{id}/unlock","/{id}/sign-out-all","/all","/{id}/deactivate","/{id}/reactivate","/{id}/role"})
    public void retiredAccountManagement(){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use platform account administration or company membership management");}

    @GetMapping("/{id}/leave-balance")
    public com.maxwell.chronos.dto.LeaveBalanceDTO getLeaveBalance(@PathVariable Long id, @RequestParam int year, @AuthenticationPrincipal Jwt jwt) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company leave balances");
    }

    @GetMapping("/me/leave-balance")
    public com.maxwell.chronos.dto.LeaveBalanceDTO getMyLeaveBalance(@RequestParam int year, @AuthenticationPrincipal Jwt jwt) {
        var requester = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null) throw new org.springframework.security.access.AccessDeniedException("Leave balance permission required");
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company leave balances");
    }

    @PutMapping("/{id}/leave-allowance")
    public com.maxwell.chronos.dto.LeaveBalanceDTO updateLeaveAllowance(@PathVariable Long id,
            @Valid @RequestBody com.maxwell.chronos.dto.LeaveAllowanceRequest request, @AuthenticationPrincipal Jwt jwt) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company leave allowances");
    }

    public record JoiningDateRequest(java.time.LocalDate joiningDate) {}

    @PatchMapping("/{id}/joining-date")
    public UserDTO updateJoiningDate(@PathVariable Long id, @RequestBody JoiningDateRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return userService.updateJoiningDate(id, request.joiningDate(),
                userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        var requester = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null || !requester.getId().equals(id)) {
            return ResponseEntity.status(403).build();
        }
        UserDTO user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<List<com.maxwell.chronos.dto.EmployeeDirectoryDTO>> getAllActiveUsers(@AuthenticationPrincipal Jwt jwt) {
        var requester = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (requester == null) return ResponseEntity.status(403).build();
        var users = userService.getEmployeeDirectory();
        var visibleIds = companyAccess.directoryUserIds(requester.getId());
        visibleIds.addAll(projectService.visibleEmployeeIds(requester));
        users = users.stream().filter(user -> visibleIds.contains(user.id()) || requester.getId().equals(user.id())).toList();
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

}
