package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.TimesheetCorrectionRequestDTO;
import com.maxwell.chronos.service.TimesheetCorrectionRequestService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/timesheet-corrections")
@RequiredArgsConstructor
public class TimesheetCorrectionRequestController {
    private final TimesheetCorrectionRequestService service;
    private final UserService users;

    public record CommentBody(String comment) {}

    @PostMapping("/{timesheetId}/projects/{projectId}")
    public ResponseEntity<TimesheetCorrectionRequestDTO> request(@PathVariable Long timesheetId,
            @PathVariable Long projectId, @RequestBody CommentBody body, @AuthenticationPrincipal Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(service.request(timesheetId, projectId, body == null ? null : body.comment(), user));
    }

    @GetMapping("/{timesheetId}/projects/{projectId}")
    public ResponseEntity<List<TimesheetCorrectionRequestDTO>> history(@PathVariable Long timesheetId,
            @PathVariable Long projectId, @AuthenticationPrincipal Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(service.history(timesheetId, projectId, user));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TimesheetCorrectionRequestDTO>> pending(@AuthenticationPrincipal Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(service.pending(user));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<TimesheetCorrectionRequestDTO> approve(@PathVariable Long requestId,
            @RequestBody(required = false) CommentBody body, @AuthenticationPrincipal Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(service.decide(requestId, true, body == null ? null : body.comment(), user));
    }

    @PostMapping("/{requestId}/decline")
    public ResponseEntity<TimesheetCorrectionRequestDTO> decline(@PathVariable Long requestId,
            @RequestBody CommentBody body, @AuthenticationPrincipal Jwt jwt) {
        var user = users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(service.decide(requestId, false, body == null ? null : body.comment(), user));
    }
}
