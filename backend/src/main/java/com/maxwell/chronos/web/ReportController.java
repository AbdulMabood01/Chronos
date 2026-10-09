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
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company reports");
    }

    @GetMapping("/project-timesheets/export")
    public ResponseEntity<byte[]> exportProjectTimesheets(@RequestParam String submissionIds,
                                                          @AuthenticationPrincipal Jwt jwt) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company reports");
    }

    @GetMapping("/vacation/export")
    public ResponseEntity<byte[]> exportVacationRequests(@RequestParam int year, @AuthenticationPrincipal Jwt jwt) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company reports");
    }

    @GetMapping("/timesheets/{timesheetId}/export")
    public ResponseEntity<byte[]> exportSingleTimesheet(@PathVariable Long timesheetId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] excel = reportService.exportTimesheetById(timesheetId, user.getId(), false);
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
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use company reports");
    }

    @GetMapping("/timesheets/{timesheetId}/pdf")
    public ResponseEntity<byte[]> exportSingleTimesheetPdf(@PathVariable Long timesheetId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] pdf = reportService.exportTimesheetPdfById(timesheetId, user.getId(), false);
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
            byte[] pdf = reportService.exportProjectTimesheetPdfById(submissionId, user.getId(), false);
            String filename = "project-timesheet-" + submissionId + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @GetMapping("/timesheet-periods/{periodId}/pdf")
    public ResponseEntity<byte[]> exportApprovalPeriodPdf(@PathVariable Long periodId,@AuthenticationPrincipal Jwt jwt) {
        var user=userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        byte[] pdf=reportService.exportApprovalPeriodPdf(periodId,user.getId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("timesheet-period-"+periodId+".pdf").build().toString()).body(pdf);
    }

    @GetMapping("/timesheet-periods")
    public ResponseEntity<List<Map<String,Object>>> approvalPeriods(@RequestParam int year,@RequestParam int month,@AuthenticationPrincipal Jwt jwt) {
        var user=userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(reportService.approvalPeriods(year,month,user.getId()));
    }

    @GetMapping("/timesheet-periods/export")
    public ResponseEntity<byte[]> exportApprovalPeriods(@RequestParam String ids,@AuthenticationPrincipal Jwt jwt) {
        var user=userService.findUserEntityByEmail(jwt.getClaimAsString("preferred_username"));
        byte[] archive=reportService.exportApprovalPeriods(parseUserIds(ids),user.getId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("approved-timesheet-periods.zip").build().toString()).body(archive);
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
