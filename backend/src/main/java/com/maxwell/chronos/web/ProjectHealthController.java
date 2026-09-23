package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.ProjectHealthDTO;
import com.maxwell.chronos.service.ProjectHealthService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/projects/health")
@RequiredArgsConstructor
public class ProjectHealthController {
    private final ProjectHealthService health;
    private final UserService users;

    @GetMapping
    public List<ProjectHealthDTO> overview(@AuthenticationPrincipal Jwt jwt) {
        return health.overview(users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")));
    }
}
