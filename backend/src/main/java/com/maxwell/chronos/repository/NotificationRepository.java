package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByEntityIdAndEntityTypeAndNotificationType(Long entityId, String entityType, String notificationType);
    List<Notification> findByUserId(Long userId);
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<Notification> findByUserIdAndIsReadFalse(Long userId);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);
}
