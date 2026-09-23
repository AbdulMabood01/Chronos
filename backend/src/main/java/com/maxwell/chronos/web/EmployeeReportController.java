package com.maxwell.chronos.web;

import com.maxwell.chronos.service.EmployeeReportService;
import com.maxwell.chronos.service.EmployeeReportService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/employee-reports")
@RequiredArgsConstructor
public class EmployeeReportController {
    private final EmployeeReportService reports;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Receipt> submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("report") Submission report,
            @RequestPart(value="attachments", required=false) List<MultipartFile> attachments) {
        return response(reports.submit(email(jwt), report, attachments));
    }
    @GetMapping
    public ResponseEntity<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) Category category, @RequestParam(required=false) Status status,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="0") int page, @RequestParam(required=false) String reportId,
            @RequestParam(required=false) Boolean anonymous) {
        return response(reports.list(email(jwt), category, status, from, to, page, reportId, anonymous));
    }
    @GetMapping("/{id}")
    public ResponseEntity<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(reports.detail(email(jwt), id));
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Review review) {
        reports.review(email(jwt), id, review); return response(null);
    }
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID attachmentId) {
        Download file = reports.download(email(jwt), id, attachmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
            .body(file.content());
    }
}
