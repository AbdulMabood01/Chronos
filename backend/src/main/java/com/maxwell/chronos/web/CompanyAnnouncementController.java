package com.maxwell.chronos.web;

import com.maxwell.chronos.service.CompanyAnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/announcements")
@RequiredArgsConstructor
public class CompanyAnnouncementController {
    private final CompanyAnnouncementService service;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private ResponseEntity<?> response(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
    @GetMapping
    public ResponseEntity<?> list(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean management) { return response(service.list(companyId,email(jwt),management)); }
    @PostMapping
    public ResponseEntity<?> create(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyAnnouncementService.Input input) { return response(service.save(companyId,email(jwt),null,input)); }
    @PutMapping("/{id}")
    public ResponseEntity<?> save(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody CompanyAnnouncementService.Input input) { return response(service.save(companyId,email(jwt),id,input)); }
    @PostMapping("/{id}/open")
    public ResponseEntity<?> open(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean management) { return response(service.open(companyId,email(jwt),id,management)); }
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam int version) { service.acknowledge(companyId,email(jwt),id,version); return response(null); }
    @PostMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam CompanyAnnouncementService.Status status,@RequestParam int version) { service.status(companyId,email(jwt),id,status,version); return response(null); }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam int version) { service.delete(companyId,email(jwt),id,version); return response(null); }
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> tracking(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return response(service.tracking(companyId,email(jwt),id)); }
    @GetMapping("/{id}/attachment")
    public ResponseEntity<?> attachment(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean management) {
        var file=service.attachment(companyId,email(jwt),id,management);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((String)file.get("attachment_name"),StandardCharsets.UTF_8).build().toString())
            .body(file.get("attachment_data"));
    }
}
