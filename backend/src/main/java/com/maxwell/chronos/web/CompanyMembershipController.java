package com.maxwell.chronos.web;

import com.maxwell.chronos.service.CompanyMembershipService;
import com.maxwell.chronos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companies/{companyId}/members/{userId}")
@RequiredArgsConstructor
public class CompanyMembershipController {
    private final CompanyMembershipService memberships;
    private final UserService users;
    private final com.maxwell.chronos.service.PasswordService passwords;
    private long actor(Jwt jwt){return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();}
    @PutMapping("/status") public CompanyMembershipService.Result status(@PathVariable long companyId,@PathVariable long userId,
            @Valid @RequestBody CompanyMembershipService.StatusInput input,@AuthenticationPrincipal Jwt jwt){
        return memberships.changeStatus(companyId,userId,actor(jwt),input);
    }
    @PostMapping("/roles") public CompanyMembershipService.Result assign(@PathVariable long companyId,@PathVariable long userId,
            @Valid @RequestBody CompanyMembershipService.RoleInput input,@AuthenticationPrincipal Jwt jwt){
        return memberships.assignRole(companyId,userId,actor(jwt),input);
    }
    @DeleteMapping("/roles/{role}") public CompanyMembershipService.Result remove(@PathVariable long companyId,@PathVariable long userId,
            @PathVariable String role,@RequestParam long version,@AuthenticationPrincipal Jwt jwt){
        return memberships.removeRole(companyId,userId,actor(jwt),role,version);
    }
    @PostMapping("/password-reset") public java.util.Map<String,String> recovery(@PathVariable long companyId,@PathVariable long userId,@AuthenticationPrincipal Jwt jwt){
        String address=memberships.recoveryAddress(companyId,userId,actor(jwt));
        passwords.forgot(address);
        return java.util.Map.of("message","Password recovery requested for the account's registered email address.");
    }
}
