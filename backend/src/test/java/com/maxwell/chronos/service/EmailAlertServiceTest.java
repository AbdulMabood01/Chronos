package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmailAlertServiceTest {
    @Test void defaultsEnableEveryCategoryAndMasterSwitchOverridesChoices() {
        var service = new EmailAlertService(mock(JdbcTemplate.class));
        for (var category : EmailAlertService.Category.values()) {
            assertTrue(service.preferences(1L).allows(category));
            assertFalse(new EmailAlertService.Preferences(false,true,true,true,true,true,true,true).allows(category));
        }
        assertFalse(new EmailAlertService.Preferences(true,true,false,true,true,true,true,true).allows(EmailAlertService.Category.TIMESHEETS));
        assertTrue(new EmailAlertService.Preferences(true,true,false,true,true,true,true,true).allows(EmailAlertService.Category.FEEDBACK));
    }
    @Test void workflowEventsUseCategoriesAndSkipDuplicateReminders() {
        var service = spy(new EmailAlertService(mock(JdbcTemplate.class)));
        doNothing().when(service).enqueue(anyLong(),any(),anyString(),anyString());
        service.notification(1L,"TIMESHEET_APPROVED");
        service.notification(1L,"VACATION_REJECTED");
        service.notification(1L,"LETTER_REQUEST_SUBMITTED");
        service.notification(1L,"TIMESHEET_REMINDER");
        verify(service).enqueue(1L,EmailAlertService.Category.TIMESHEETS,"Chronos: timesheets approved","/notifications");
        verify(service).enqueue(1L,EmailAlertService.Category.VACATION,"Chronos: vacation rejected","/notifications");
        verify(service).enqueue(1L,EmailAlertService.Category.LETTERS,"Chronos: letters submitted","/notifications");
        verify(service,times(3)).enqueue(anyLong(),any(),anyString(),anyString());
    }
    @Test void disabledCategoryDoesNotQueueMail() {
        var db = mock(JdbcTemplate.class);
        var service = spy(new EmailAlertService(db));
        doReturn(new EmailAlertService.Preferences(true,true,true,true,true,true,false,true)).when(service).preferences(1L);
        service.enqueue(1L,EmailAlertService.Category.FEEDBACK,"Chronos: feedback received","/feedback");
        verifyNoInteractions(db);
    }
}
