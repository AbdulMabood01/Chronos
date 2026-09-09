package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.NotificationDTO;
import com.maxwell.chronos.service.NotificationService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getNotifications(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        List<NotificationDTO> notifications = notificationService.getUserNotifications(user.getId());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotifications(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(user.getId());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(count);
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("preferred_username");
        var user = userService.findUserEntityByEmail(email);

        if (user == null) {
            return ResponseEntity.status(403).build();
        }

        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
