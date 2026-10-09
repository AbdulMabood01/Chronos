package com.maxwell.chronos.web;
import com.maxwell.chronos.service.CompanySettingsService;
import com.maxwell.chronos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/companies/{companyId}/settings")
@RequiredArgsConstructor
public class CompanySettingsController {
    private final CompanySettingsService settings;
    private final UserService users;
    private long actor(Jwt jwt){return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();}
    @GetMapping public CompanySettingsService.Snapshot get(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return settings.get(companyId,actor(jwt));}
    @PutMapping("/{key}") public CompanySettingsService.Snapshot update(@PathVariable long companyId,@PathVariable String key,
            @Valid @RequestBody CompanySettingsService.Input input,@AuthenticationPrincipal Jwt jwt){return settings.update(companyId,actor(jwt),key,input);}
}
