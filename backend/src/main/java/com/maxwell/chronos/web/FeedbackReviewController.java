package com.maxwell.chronos.web;

import com.maxwell.chronos.service.FeedbackReviewService;
import com.maxwell.chronos.service.FeedbackReviewService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/feedback-reviews")
@RequiredArgsConstructor
public class FeedbackReviewController {
    private final FeedbackReviewService service;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private <T> ResponseEntity<T> response(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
    @GetMapping("/employees")
    public ResponseEntity<?> search(@AuthenticationPrincipal Jwt jwt, @RequestParam String query) { return response(service.search(email(jwt),query)); }
    @PostMapping("/feedback")
    public ResponseEntity<?> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Feedback input) { return response(service.submit(email(jwt),input)); }
    @GetMapping("/feedback")
    public ResponseEntity<?> feedback(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="false") boolean given) { return response(service.feedback(email(jwt),given)); }
    @GetMapping("/reviews")
    public ResponseEntity<?> reviews(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) Long employeeId) { return response(service.reviews(email(jwt),employeeId)); }
    @PostMapping("/reviews")
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Review input) { return response(service.save(email(jwt),null,input)); }
    @PutMapping("/reviews/{id}")
    public ResponseEntity<?> save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Review input) { return response(service.save(email(jwt),id,input)); }
    @PostMapping("/reviews/{id}/publish")
    public ResponseEntity<?> publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam int version) { service.publish(email(jwt),id,version); return response(null); }
    @GetMapping("/reviews/{id}/audit")
    public ResponseEntity<?> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return response(service.history(email(jwt),id)); }
}
