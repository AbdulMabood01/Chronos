package com.maxwell.chronos.web;

import com.maxwell.chronos.service.CompanyManagementService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyManagementService companies;
    private final UserService users;

    private long actor(Jwt jwt) {
        return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();
    }

    public record CompanyInput(String name, String slug) {}
    public record InvitationInput(Long projectId, String email, String role, Long accessRequestId) {}
    public record AcceptInput(String token) {}
    // Keep credentials out of generated toString methods and logs.
    public static class ClaimInput {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") public String token;
        @NotBlank @Size(max=100) public String firstName;
        @NotBlank @Size(max=100) public String lastName;
        @NotBlank @Size(max=72) public String password;
    }
    public static class AccessRequestInput {
        @NotBlank @Size(max=80) public String slug;
        @NotBlank @Size(max=100) public String firstName;
        @NotBlank @Size(max=100) public String lastName;
        @NotBlank @Email @Size(max=255) public String email;
    }
    public record ModeratorInput(long projectId, long userId, boolean timesheets, boolean expenses,
                                 LocalDate startsOn, LocalDate endsOn) {}
    public record OwnershipInput(long userId) {}

    @GetMapping public List<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt) {
        return companies.myCompanies(actor(jwt));
    }

    @PostMapping public Map<String, Long> create(@RequestBody CompanyInput input, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("id", companies.createCompany(input.name(), input.slug(), actor(jwt)));
    }

    @GetMapping("/{companyId}/members") public List<Map<String, Object>> members(
            @PathVariable long companyId, @AuthenticationPrincipal Jwt jwt) {
        return companies.members(companyId, actor(jwt));
    }

    @PostMapping("/{companyId}/invitations") public void invite(@PathVariable long companyId,
            @RequestBody InvitationInput input, @AuthenticationPrincipal Jwt jwt) {
        companies.invite(companyId, input.projectId(), input.email(), input.role(), actor(jwt), input.accessRequestId());
    }

    @GetMapping("/{companyId}/invitations") public List<Map<String, Object>> invitations(
            @PathVariable long companyId, @AuthenticationPrincipal Jwt jwt) {
        return companies.invitations(companyId, actor(jwt));
    }

    @DeleteMapping("/{companyId}/invitations/{invitationId}") public void revokeInvitation(
            @PathVariable long companyId, @PathVariable long invitationId, @AuthenticationPrincipal Jwt jwt) {
        companies.revokeInvitation(companyId, invitationId, actor(jwt));
    }

    @PostMapping("/invitations/accept") public void accept(@RequestBody AcceptInput input,
            @AuthenticationPrincipal Jwt jwt) {
        companies.accept(input.token(), actor(jwt));
    }

    @PostMapping("/invitations/preview") public CompanyManagementService.InvitationDetails preview(
            @RequestBody AcceptInput input) {
        return companies.previewInvitation(input.token());
    }

    @PostMapping("/invitations/claim") public Map<String, String> claim(@Valid @RequestBody ClaimInput input) {
        return Map.of("email", companies.claimInvitation(input.token, input.firstName, input.lastName, input.password));
    }

    @PostMapping("/access-requests") public Map<String, String> requestAccess(@Valid @RequestBody AccessRequestInput input) {
        companies.requestAccess(input.slug, input.firstName, input.lastName, input.email);
        return Map.of("message", "Request received. If the workspace ID is valid, its administrator can review it and send an invitation.");
    }

    @GetMapping("/{companyId}/access-requests") public List<Map<String, Object>> accessRequests(
            @PathVariable long companyId, @AuthenticationPrincipal Jwt jwt) {
        return companies.accessRequests(companyId, actor(jwt));
    }

    @DeleteMapping("/{companyId}/access-requests/{requestId}") public void dismissAccessRequest(
            @PathVariable long companyId, @PathVariable long requestId, @AuthenticationPrincipal Jwt jwt) {
        companies.dismissAccessRequest(companyId, requestId, actor(jwt));
    }

    @GetMapping("/invitations/mine") public List<CompanyManagementService.InvitationDetails> myPendingInvitations(
            @AuthenticationPrincipal Jwt jwt) {
        return companies.myPendingInvitations(actor(jwt));
    }

    @PostMapping("/invitations/{invitationId}/accept") public void acceptById(
            @PathVariable long invitationId, @AuthenticationPrincipal Jwt jwt) {
        companies.acceptById(invitationId, actor(jwt));
    }

    @PostMapping("/{companyId}/moderator-grants") public void grant(@PathVariable long companyId,
            @RequestBody ModeratorInput input, @AuthenticationPrincipal Jwt jwt) {
        companies.grantModerator(companyId, input.projectId(), input.userId(), input.timesheets(),
                input.expenses(), input.startsOn(), input.endsOn(), actor(jwt));
    }

    @GetMapping("/{companyId}/moderator-grants") public List<Map<String, Object>> moderatorGrants(
            @PathVariable long companyId, @AuthenticationPrincipal Jwt jwt) {
        return companies.moderatorGrants(companyId, actor(jwt));
    }

    @DeleteMapping("/{companyId}/moderator-grants/{grantId}") public void revoke(@PathVariable long companyId,
            @PathVariable long grantId, @AuthenticationPrincipal Jwt jwt) {
        companies.revokeModerator(companyId, grantId, actor(jwt));
    }

    @DeleteMapping("/{companyId}/roles/{role}/users/{userId}") public void removeRole(
            @PathVariable long companyId, @PathVariable String role, @PathVariable long userId,
            @RequestParam(required = false) Long projectId, @AuthenticationPrincipal Jwt jwt) {
        companies.removeRole(companyId, projectId, userId, role, actor(jwt));
    }

    @PostMapping("/{companyId}/projects/{projectId}/owner") public void transferOwner(
            @PathVariable long companyId, @PathVariable long projectId, @RequestBody OwnershipInput input,
            @AuthenticationPrincipal Jwt jwt) {
        companies.transferProjectOwnership(companyId, projectId, input.userId(), actor(jwt));
    }

    @GetMapping("/{companyId}/projects/{projectId}/roles") public List<Map<String, Object>> projectRoles(
            @PathVariable long companyId, @PathVariable long projectId, @AuthenticationPrincipal Jwt jwt) {
        return companies.projectRoles(companyId, projectId, actor(jwt));
    }
}
