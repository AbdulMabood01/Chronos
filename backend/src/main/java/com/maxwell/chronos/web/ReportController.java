package com.maxwell.chronos.web;

import com.maxwell.chronos.service.ReportService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final UserService userService;

    @GetMapping("/timesheets/export")
    public ResponseEntity<byte[]> exportTimesheets(@RequestParam int year, @RequestParam int month,
                                                    @RequestParam(required = false) String userIds,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isAdmin() && !user.isProjectAdmin())) {
            return ResponseEntity.status(403).build();
        }

        byte[] reports = reportService.exportTimesheets(year, month, parseUserIds(userIds));
        String filename = "timesheets-" + year + "-" + month + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(reports);
    }

    @GetMapping("/project-timesheets/export")
    public ResponseEntity<byte[]> exportProjectTimesheets(@RequestParam String submissionIds,
                                                          @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isAdmin() && !user.isProjectAdmin())) {
            return ResponseEntity.status(403).build();
        }

        byte[] reports = reportService.exportProjectTimesheets(parseUserIds(submissionIds));
        String filename = "project-timesheets.zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(reports);
    }

    @GetMapping("/vacation/export")
    public ResponseEntity<byte[]> exportVacationRequests(@RequestParam int year, @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isAdmin() && !user.isProjectAdmin())) {
            return ResponseEntity.status(403).build();
        }

        byte[] excel = reportService.exportVacationRequests(year);
        String filename = "vacation-requests-" + year + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(excel);
    }

    @GetMapping("/timesheets/{timesheetId}/export")
    public ResponseEntity<byte[]> exportSingleTimesheet(@PathVariable Long timesheetId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] excel = reportService.exportTimesheetById(timesheetId, user.getId(), user.isAdmin() || user.isProjectAdmin());
            String filename = "timesheet-" + timesheetId + ".xlsx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(excel);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> getMonthlySummary(@RequestParam int year, @RequestParam int month,
                                                                        @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null || (!user.isAdmin() && !user.isProjectAdmin())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(reportService.getMonthlySummary(year, month));
    }

    @GetMapping("/timesheets/{timesheetId}/pdf")
    public ResponseEntity<byte[]> exportSingleTimesheetPdf(@PathVariable Long timesheetId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] pdf = reportService.exportTimesheetPdfById(timesheetId, user.getId(), user.isAdmin() || user.isProjectAdmin());
            String filename = "timesheet-" + timesheetId + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @GetMapping("/timesheet-projects/{submissionId}/pdf")
    public ResponseEntity<byte[]> exportProjectTimesheetPdf(@PathVariable Long submissionId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] pdf = reportService.exportProjectTimesheetPdfById(submissionId, user.getId(), user.isAdmin() || user.isProjectAdmin());
            String filename = "project-timesheet-" + submissionId + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    private List<Long> parseUserIds(String userIds) {
        if (userIds == null || userIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }
}
