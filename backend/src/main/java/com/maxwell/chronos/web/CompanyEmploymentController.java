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
    private long actor(Jwt jwt) { return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId(); }

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
