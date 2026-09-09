package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.AuditLog;
import com.maxwell.chronos.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserId(Long userId);
    List<AuditLog> findByAction(AuditAction action);
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
    List<AuditLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}
