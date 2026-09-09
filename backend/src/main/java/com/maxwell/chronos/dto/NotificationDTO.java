package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private Long id;
    private String notificationType;
    private String title;
    private String message;
    private Boolean isRead;
    private Long entityId;
    private String entityType;
    private LocalDateTime createdAt;
}
