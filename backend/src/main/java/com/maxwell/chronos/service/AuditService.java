package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.AuditLog;
import com.maxwell.chronos.dto.AuditLogDTO;
import com.maxwell.chronos.enums.AuditAction;
import com.maxwell.chronos.repository.AuditLogRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void logAction(Long userId, String action, String entityType, Long entityId, String details) {
        try {
            ObjectNode detailsNode = objectMapper.createObjectNode();
            detailsNode.put("message", details != null ? details : "");

            AuditLog auditLog = AuditLog.builder()
                    .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
                    .action(AuditAction.valueOf(action))
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(detailsNode)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't fail the transaction
            System.err.println("Error logging audit action: " + e.getMessage());
        }
    }

    public List<AuditLogDTO> getAuditLogs() {
        return auditLogRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AuditLogDTO> getAuditLogsByUserId(Long userId) {
        return auditLogRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<AuditLogDTO> getAuditLogsByEntityTypeAndId(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private AuditLogDTO toDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userName(log.getUser() != null ? log.getUser().getFullName() : null)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
