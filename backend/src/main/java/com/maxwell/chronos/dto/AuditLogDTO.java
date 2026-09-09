package com.maxwell.chronos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.maxwell.chronos.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String userName;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private JsonNode details;
    private LocalDateTime createdAt;
}
