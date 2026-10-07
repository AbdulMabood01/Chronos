package com.maxwell.chronos.web;
import com.maxwell.chronos.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequiredArgsConstructor
public class SettingsController {
    private final PlatformSettingsService settings;
    private final UserService users;
    private long actor(Jwt jwt){return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();}
    @GetMapping("/platform/settings") public PlatformSettingsService.Snapshot get(@AuthenticationPrincipal Jwt jwt){return settings.get(actor(jwt));}
    @PutMapping("/platform/settings/{key}") public PlatformSettingsService.Snapshot update(@PathVariable String key,
        @Valid @RequestBody CompanySettingsService.Input input,@AuthenticationPrincipal Jwt jwt){return settings.update(actor(jwt),key,input);}
    // Retire global business-policy endpoints, including bulk allowance application.
    @RequestMapping(value={"/settings","/settings/{key}","/settings/leave-defaults/preview","/settings/leave-defaults/apply"},
        method={RequestMethod.GET,RequestMethod.PUT,RequestMethod.POST})
    public void retired(){throw new ResponseStatusException(HttpStatus.GONE,"Global settings have moved to company and platform settings");}
}
