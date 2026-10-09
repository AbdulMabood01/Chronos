package com.maxwell.chronos.web;

import com.maxwell.chronos.service.CompanyFeedbackReviewService;
import com.maxwell.chronos.service.CompanyFeedbackReviewService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/companies/{companyId}/feedback-reviews")
@RequiredArgsConstructor
public class CompanyFeedbackReviewController {
    private final CompanyFeedbackReviewService service;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private <T> ResponseEntity<T> response(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
    @GetMapping("/employees")
    public ResponseEntity<?> search(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @RequestParam String query) { return response(service.search(companyId,email(jwt),query)); }
    @PostMapping("/feedback")
    public ResponseEntity<?> submit(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Feedback input) { return response(service.submit(companyId,email(jwt),input)); }
    @GetMapping("/feedback")
    public ResponseEntity<?> feedback(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="false") boolean given) { return response(service.feedback(companyId,email(jwt),given)); }
    @GetMapping("/reviews")
    public ResponseEntity<?> reviews(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) Long employeeId) { return response(service.reviews(companyId,email(jwt),employeeId)); }
    @PostMapping("/reviews")
    public ResponseEntity<?> create(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Review input) { return response(service.save(companyId,email(jwt),null,input)); }
    @PutMapping("/reviews/{id}")
    public ResponseEntity<?> save(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Review input) { return response(service.save(companyId,email(jwt),id,input)); }
    @PostMapping("/reviews/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam int version) { service.publish(companyId,email(jwt),id,version); return response(null); }
    @GetMapping("/reviews/{id}/audit")
    public ResponseEntity<?> history(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return response(service.history(companyId,email(jwt),id)); }
}
