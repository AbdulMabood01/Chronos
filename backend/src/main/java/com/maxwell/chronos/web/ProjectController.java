package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.ProjectDTO;
import com.maxwell.chronos.dto.ProjectHoursDashboardDTO;
import com.maxwell.chronos.dto.SaveProjectRequest;
import com.maxwell.chronos.service.ProjectService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getProjects(@AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.getProjects(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/assigned")
    public ResponseEntity<List<ProjectDTO>> getAssignedProjects(@RequestParam(required = false) Integer year,
                                                                @RequestParam(required = false) Integer month,
                                                                @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(projectService.getAssignedProjects(user.getId(), year, month));
    }

    @GetMapping("/hours-dashboard")
    public ResponseEntity<List<ProjectHoursDashboardDTO>> getProjectHoursDashboard(@RequestParam int year,
                                                                                   @RequestParam int month,
                                                                                   @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.getProjectHoursDashboard(year, month, user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(@RequestBody SaveProjectRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.saveProject(null, request, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectDTO> updateProject(@PathVariable Long projectId,
                                                    @RequestBody SaveProjectRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.saveProject(projectId, request, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/{projectId}/assignments/{userId}")
    public ResponseEntity<ProjectDTO> assignEmployee(@PathVariable Long projectId, @PathVariable Long userId,
                                                     @RequestParam LocalDate startDate,
                                                     @RequestParam LocalDate endDate,
                                                     @RequestParam BigDecimal billRate,
                                                     @RequestParam BigDecimal plannedHours,
                                                     @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.assignEmployee(projectId, userId, startDate, endDate, billRate, plannedHours, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @DeleteMapping("/{projectId}/assignments/{userId}")
    public ResponseEntity<ProjectDTO> removeEmployee(@PathVariable Long projectId, @PathVariable Long userId,
                                                     @RequestParam(required = false) Long replacementManagerId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.removeEmployee(projectId, userId, replacementManagerId, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PatchMapping("/{projectId}/assignments/{userId}/dates")
    public ResponseEntity<ProjectDTO> updateAssignmentDates(@PathVariable Long projectId,
                                                            @PathVariable Long userId,
                                                            @RequestParam LocalDate startDate,
                                                            @RequestParam LocalDate endDate,
                                                            @RequestParam BigDecimal billRate,
                                                            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            return ResponseEntity.ok(projectService.updateAssignmentDates(projectId, userId, startDate, endDate, billRate, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PatchMapping("/{projectId}/assignments/{userId}/planned-hours")
    public ResponseEntity<ProjectDTO> updatePlannedHours(@PathVariable Long projectId,
                                                         @PathVariable Long userId,
                                                         @RequestParam(required = false) Integer year,
                                                         @RequestParam(required = false) Integer month,
                                                         @RequestParam(required = false) BigDecimal plannedHours,
                                                         @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        try {
            if (year != null || month != null) {
                return ResponseEntity.ok(projectService.updatePlannedHours(projectId, userId, year, month, plannedHours, user));
            }
            return ResponseEntity.ok(projectService.updatePlannedHours(projectId, userId, plannedHours, user));
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }
}
