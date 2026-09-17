package com.maxwell.chronos.service;

import com.maxwell.chronos.repository.NotificationRepository;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test void readsNotificationsNewestFirst() {
        var notifications = mock(NotificationRepository.class);
        var service = new NotificationService(notifications, mock(UserRepository.class));
        when(notifications.findByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());
        when(notifications.findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());

        service.getUserNotifications(1L);
        service.getUnreadNotifications(1L);

        verify(notifications).findByUserIdOrderByCreatedAtDescIdDesc(1L);
        verify(notifications).findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(1L);
        verify(notifications, never()).findByUserId(1L);
        verify(notifications, never()).findByUserIdAndIsReadFalse(1L);
    }
}
