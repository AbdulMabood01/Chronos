package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.VacationRequestDTO;
import com.maxwell.chronos.enums.VacationType;
import com.maxwell.chronos.service.VacationService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/vacation")
@RequiredArgsConstructor
public class VacationController {
    private final VacationService vacationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<VacationRequestDTO> createVacationRequest(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String type,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            VacationType vacationType = VacationType.valueOf(type);
            VacationRequestDTO request = vacationService.createVacationRequest(user.getId(), start, end, vacationType, notes);
            return ResponseEntity.ok(request);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{vacationId}")
    public ResponseEntity<VacationRequestDTO> updateVacationRequest(
            @PathVariable Long vacationId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String type,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            VacationType vacationType = VacationType.valueOf(type);
            VacationRequestDTO request = vacationService.updateVacationRequest(vacationId, start, end, vacationType, notes, user.getId());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{vacationId}/submit")
    public ResponseEntity<VacationRequestDTO> submitVacationRequest(
            @PathVariable Long vacationId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            VacationRequestDTO request = vacationService.submitVacationRequest(vacationId, user.getId());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{vacationId}")
    public ResponseEntity<Void> deleteVacationRequest(
            @PathVariable Long vacationId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            vacationService.deleteVacationRequest(vacationId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<List<VacationRequestDTO>> getPendingVacationRequests(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<VacationRequestDTO> requests = vacationService.getPendingVacationRequests();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/my")
    public ResponseEntity<List<VacationRequestDTO>> getMyVacationRequests(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        List<VacationRequestDTO> requests = vacationService.getVacationRequestsByUser(user.getId());
        return ResponseEntity.ok(requests);
    }
}
