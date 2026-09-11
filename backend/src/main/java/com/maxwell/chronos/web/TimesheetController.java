package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.TimesheetDTO;
import com.maxwell.chronos.dto.TimesheetProjectSubmissionDTO;
import com.maxwell.chronos.dto.TimeEntryDTO;
import com.maxwell.chronos.dto.CreateTimeEntryRequest;
import com.maxwell.chronos.dto.UpdateTimeEntryRequest;
import com.maxwell.chronos.service.TimesheetService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/timesheets")
@RequiredArgsConstructor
public class TimesheetController {
    private final TimesheetService timesheetService;
    private final UserService userService;

    @GetMapping("/{year}/{month}")
    public ResponseEntity<TimesheetDTO> getTimesheet(@PathVariable int year, @PathVariable int month,
                                                     @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        TimesheetDTO timesheet = timesheetService.getOrCreateTimesheet(user.getId(), year, month);
        return ResponseEntity.ok(timesheet);
    }

    @PostMapping
    public ResponseEntity<TimesheetDTO> getOrCreateTimesheet(@RequestParam int year, @RequestParam int month,
                                                            @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        TimesheetDTO timesheet = timesheetService.getOrCreateTimesheet(user.getId(), year, month);
        return ResponseEntity.ok(timesheet);
    }

    @GetMapping("/id/{timesheetId}")
    public ResponseEntity<TimesheetDTO> getTimesheetById(@PathVariable Long timesheetId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetDTO timesheet = timesheetService.getTimesheetById(timesheetId, user.getId(), user.isSuperAdmin() || user.isAdmin());
            return ResponseEntity.ok(timesheet);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/{timesheetId}/time-entries")
    public ResponseEntity<TimeEntryDTO> addTimeEntry(@PathVariable Long timesheetId,
                                                     @RequestBody CreateTimeEntryRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            LocalDate entryDate = LocalDate.parse(request.getEntryDate());
            BigDecimal hours = new BigDecimal(request.getHours());
            TimeEntryDTO entry = timesheetService.addTimeEntry(timesheetId, entryDate, hours,
                    request.getNotes(), request.getProjectId(), request.getSessions(), user.getId());
            return ResponseEntity.ok(entry);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PutMapping("/{timesheetId}/time-entries/{entryId}")
    public ResponseEntity<TimeEntryDTO> updateTimeEntry(@PathVariable Long timesheetId, @PathVariable Long entryId,
                                                        @RequestBody UpdateTimeEntryRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            BigDecimal hours = new BigDecimal(request.getHours());
            TimeEntryDTO entry = timesheetService.updateTimeEntry(timesheetId, entryId, hours,
                    request.getNotes(), request.getProjectId(), request.getSessions(), user.getId());
            return ResponseEntity.ok(entry);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @DeleteMapping("/{timesheetId}/time-entries/{entryId}")
    public ResponseEntity<Void> deleteTimeEntry(@PathVariable Long timesheetId, @PathVariable Long entryId,
                                                @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            timesheetService.deleteTimeEntry(timesheetId, entryId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/{timesheetId}/reopen")
    public ResponseEntity<TimesheetDTO> reopenTimesheet(@PathVariable Long timesheetId,
                                                        @RequestBody(required = false) java.util.Map<String, String> body,
                                                        @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null || !user.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            String reason = body != null ? body.get("reason") : null;
            TimesheetDTO timesheet = timesheetService.reopenTimesheet(timesheetId, reason, user.getId());
            return ResponseEntity.ok(timesheet);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PostMapping("/{timesheetId}/submit")
    public ResponseEntity<TimesheetDTO> submitTimesheet(@PathVariable Long timesheetId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetDTO timesheet = timesheetService.submitTimesheet(timesheetId, user.getId());
            return ResponseEntity.ok(timesheet);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @GetMapping("/{timesheetId}/projects/{projectId}/submission")
    public ResponseEntity<TimesheetProjectSubmissionDTO> getProjectSubmission(@PathVariable Long timesheetId,
                                                                              @PathVariable Long projectId,
                                                                              @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetProjectSubmissionDTO submission = timesheetService.getProjectSubmission(timesheetId, projectId, user);
            return ResponseEntity.ok(submission);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/{timesheetId}/projects/{projectId}/submit")
    public ResponseEntity<TimesheetProjectSubmissionDTO> submitProjectTimesheet(@PathVariable Long timesheetId,
                                                                                @PathVariable Long projectId,
                                                                                @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            TimesheetProjectSubmissionDTO submission = timesheetService.submitProjectTimesheet(timesheetId, projectId, user.getId());
            return ResponseEntity.ok(submission);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TimesheetDTO>> getPendingTimesheets(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            List<TimesheetDTO> timesheets = timesheetService.getPendingTimesheets(user);
            return ResponseEntity.ok(timesheets);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/project-submissions/pending")
    public ResponseEntity<List<TimesheetProjectSubmissionDTO>> getPendingProjectSubmissions(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            List<TimesheetProjectSubmissionDTO> submissions = timesheetService.getPendingProjectSubmissions(user);
            return ResponseEntity.ok(submissions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<TimesheetDTO>> getMyTimesheets(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        List<TimesheetDTO> timesheets = timesheetService.getTimesheetsByUser(user.getId());
        return ResponseEntity.ok(timesheets);
    }
}
