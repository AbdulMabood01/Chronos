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
    private final com.maxwell.chronos.service.CompanyAccessService access;

    @GetMapping
    public List<ProjectHealthDTO> overview(@RequestParam(required=false) Long companyId,@AuthenticationPrincipal Jwt jwt) {
        var actor=users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));if(companyId!=null)access.requireActiveCompanyAccess(companyId,actor.getId());
        return health.overview(actor).stream().filter(p->companyId==null||companyId.equals(access.companyId(p.projectId()))).toList();
    }
}
