package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.LetterRequestDTO;
import com.maxwell.chronos.dto.TimesheetDTO;
import com.maxwell.chronos.dto.TimesheetProjectSubmissionDTO;
import com.maxwell.chronos.dto.VacationRequestDTO;
import com.maxwell.chronos.service.LetterRequestService;
import com.maxwell.chronos.service.TimesheetService;
import com.maxwell.chronos.service.VacationService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalsController {
    private final TimesheetService timesheetService;
    private final VacationService vacationService;
    private final LetterRequestService letterRequestService;
    private final UserService userService;

    @PostMapping("/timesheet/{timesheetId}/approve")
    public ResponseEntity<TimesheetDTO> approveTimesheet(
            @PathVariable Long timesheetId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetDTO timesheet = timesheetService.approveTimesheet(timesheetId, user.getId());
            return ResponseEntity.ok(timesheet);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/timesheet/{timesheetId}/reject")
    public ResponseEntity<TimesheetDTO> rejectTimesheet(
            @PathVariable Long timesheetId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            String rejectionReason = reason != null ? reason : body != null ? body.get("reason") : null;
            TimesheetDTO timesheet = timesheetService.rejectTimesheet(timesheetId, rejectionReason, user.getId());
            return ResponseEntity.ok(timesheet);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/vacation/{vacationId}/approve")
    public ResponseEntity<VacationRequestDTO> approveVacation(
            @PathVariable Long vacationId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            VacationRequestDTO vacation = vacationService.approveVacationRequest(vacationId, user.getId());
            return ResponseEntity.ok(vacation);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/vacation/{vacationId}/reject")
    public ResponseEntity<VacationRequestDTO> rejectVacation(
            @PathVariable Long vacationId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            String rejectionReason = reason != null ? reason : body != null ? body.get("reason") : null;
            VacationRequestDTO vacation = vacationService.rejectVacationRequest(vacationId, rejectionReason, user.getId());
            return ResponseEntity.ok(vacation);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/timesheet-project/{submissionId}/approve")
    public ResponseEntity<TimesheetProjectSubmissionDTO> approveProjectTimesheet(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetProjectSubmissionDTO submission = timesheetService.approveProjectSubmission(submissionId, user.getId());
            return ResponseEntity.ok(submission);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/timesheet-project/{submissionId}/reject")
    public ResponseEntity<TimesheetProjectSubmissionDTO> rejectProjectTimesheet(
            @PathVariable Long submissionId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            String rejectionReason = reason != null ? reason : body != null ? body.get("reason") : null;
            TimesheetProjectSubmissionDTO submission = timesheetService.rejectProjectSubmission(submissionId, rejectionReason, user.getId());
            return ResponseEntity.ok(submission);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/letter-request/{requestId}/approve")
    public ResponseEntity<LetterRequestDTO> approveLetterRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            LetterRequestDTO request = letterRequestService.approveLetterRequest(requestId, user.getId());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/letter-request/{requestId}/reject")
    public ResponseEntity<LetterRequestDTO> rejectLetterRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            String rejectionReason = reason != null ? reason : body != null ? body.get("reason") : null;
            LetterRequestDTO request = letterRequestService.rejectLetterRequest(requestId, rejectionReason, user.getId());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }
}
