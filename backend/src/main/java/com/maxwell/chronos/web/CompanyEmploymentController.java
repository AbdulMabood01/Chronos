package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.CompanyEmploymentDTO;
import com.maxwell.chronos.service.CompanyEmploymentService;
import com.maxwell.chronos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companies/{companyId}/members")
@RequiredArgsConstructor
public class CompanyEmploymentController {
    private final CompanyEmploymentService employment;
    private final UserService users;
    private final com.maxwell.chronos.service.MemberDetailCorrectionService corrections;
    private final com.maxwell.chronos.service.CompanyMemberProfileService profiles;
    private long actor(Jwt jwt) { return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId(); }

    public record CorrectionInput(@jakarta.validation.constraints.NotBlank String kind,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason) {}
    public record DecisionInput(boolean approve,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason) {}
    @GetMapping("/{userId}/corrections") public java.util.List<java.util.Map<String,Object>> corrections(@PathVariable long companyId,@PathVariable long userId,@AuthenticationPrincipal Jwt jwt){return corrections.list(companyId,userId,actor(jwt));}
    @PostMapping("/{userId}/corrections") public java.util.Map<String,Long> requestCorrection(@PathVariable long companyId,@PathVariable long userId,@Valid @RequestBody CorrectionInput input,@AuthenticationPrincipal Jwt jwt){return java.util.Map.of("id",corrections.request(companyId,userId,actor(jwt),input.kind(),input.reason()));}
    @PostMapping("/{userId}/corrections/{id}/decision") public void decideCorrection(@PathVariable long companyId,@PathVariable long userId,@PathVariable long id,@Valid @RequestBody DecisionInput input,@AuthenticationPrincipal Jwt jwt){corrections.decide(companyId,userId,id,actor(jwt),input.approve(),input.reason());}
    @GetMapping("/{userId}/profile") public com.maxwell.chronos.dto.UserDTO profile(@PathVariable long companyId,@PathVariable long userId,@AuthenticationPrincipal Jwt jwt) {
        return profiles.get(companyId,userId,actor(jwt));
    }
    @PutMapping("/{userId}/profile") public com.maxwell.chronos.dto.UserDTO updateProfile(@PathVariable long companyId,@PathVariable long userId,@Valid @RequestBody com.maxwell.chronos.dto.UpdateProfileRequest input,@AuthenticationPrincipal Jwt jwt) {
        return profiles.update(companyId,userId,actor(jwt),input);
    }
    @GetMapping("/me/employment") public CompanyEmploymentDTO mine(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt) {
        long id=actor(jwt); return employment.get(companyId,id,id);
    }
    @GetMapping("/{userId}/employment") public CompanyEmploymentDTO get(@PathVariable long companyId,
            @PathVariable long userId,@AuthenticationPrincipal Jwt jwt) { return employment.get(companyId,userId,actor(jwt)); }
    @PutMapping("/{userId}/employment") public CompanyEmploymentDTO update(@PathVariable long companyId,
            @PathVariable long userId,@Valid @RequestBody CompanyEmploymentService.Input input,@AuthenticationPrincipal Jwt jwt) {
        return employment.update(companyId,userId,actor(jwt),input);
    }
}
