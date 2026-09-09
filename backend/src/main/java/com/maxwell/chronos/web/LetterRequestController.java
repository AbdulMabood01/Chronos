package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.CreateLetterRequest;
import com.maxwell.chronos.dto.LetterRequestDTO;
import com.maxwell.chronos.service.LetterRequestService;
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

@RestController
@RequestMapping("/letter-requests")
@RequiredArgsConstructor
public class LetterRequestController {
    private final LetterRequestService letterRequestService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<LetterRequestDTO> createLetterRequest(
            @RequestBody CreateLetterRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            return ResponseEntity.ok(letterRequestService.createLetterRequest(user.getId(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<LetterRequestDTO>> getMyLetterRequests(@AuthenticationPrincipal Jwt jwt) {
        var user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(letterRequestService.getMyRequests(user.getId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<LetterRequestDTO>> getPendingLetterRequests(@AuthenticationPrincipal Jwt jwt) {
        var user = currentUser(jwt);
        if (user == null || (!user.isSuperAdmin() && !user.isAdmin())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(letterRequestService.getPendingRequests());
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<LetterRequestDTO> getLetterRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        var user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            return ResponseEntity.ok(letterRequestService.getRequest(requestId, user.getId(), user.isSuperAdmin() || user.isAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{requestId}/pdf")
    public ResponseEntity<byte[]> downloadLetterRequestPdf(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        var user = currentUser(jwt);
        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            boolean canReviewLetters = user.isSuperAdmin() || user.isAdmin();
            byte[] pdf = letterRequestService.generatePdf(requestId, user.getId(), canReviewLetters);
            String filename = letterRequestService.buildFilename(requestId, user.getId(), canReviewLetters);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private com.maxwell.chronos.domain.User currentUser(Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        return userService.findUserEntityByEmail(email);
    }
}
