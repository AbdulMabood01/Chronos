package com.maxwell.chronos.web;

import com.maxwell.chronos.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private ResponseEntity<?> response(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean management) { return response(service.list(email(jwt),management)); }
    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AnnouncementService.Input input) { return response(service.save(email(jwt),null,input)); }
    @PutMapping("/{id}")
    public ResponseEntity<?> save(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody AnnouncementService.Input input) { return response(service.save(email(jwt),id,input)); }
    @PostMapping("/{id}/open")
    public ResponseEntity<?> open(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean management) { return response(service.open(email(jwt),id,management)); }
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam int version) { service.acknowledge(email(jwt),id,version); return response(null); }
    @PostMapping("/{id}/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam AnnouncementService.Status status,@RequestParam int version) { service.status(email(jwt),id,status,version); return response(null); }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam int version) { service.delete(email(jwt),id,version); return response(null); }
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> tracking(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return response(service.tracking(email(jwt),id)); }
    @GetMapping("/{id}/attachment")
    public ResponseEntity<?> attachment(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean management) {
        var file=service.attachment(email(jwt),id,management);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((String)file.get("attachment_name"),StandardCharsets.UTF_8).build().toString())
            .body(file.get("attachment_data"));
    }
}
