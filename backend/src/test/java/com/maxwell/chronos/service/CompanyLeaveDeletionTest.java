package com.maxwell.chronos.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CompanyLeaveDeletionTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final CompanyLeaveService leave=new CompanyLeaveService(db,mock(CompanyAccessService.class),mock(AuditService.class),mock(NotificationService.class));
    void request(String status) {
        when(db.queryForList(anyString(),eq(12L),eq(7L))).thenReturn(List.of(Map.of("id",7L,"user_id",5L,"version",2L,"status",status)));
    }
    @Test void submittedOwnerCanDelete() {
        request("SUBMITTED");leave.delete(12,7,5,2);
        verify(db).update("DELETE FROM vacation_requests WHERE id=?",7L);
    }
    @Test void otherUsersAndFinalizedRequestsCannotDelete() {
        request("SUBMITTED");assertThrows(AccessDeniedException.class,()->leave.delete(12,7,6,2));
        request("APPROVED");assertThrows(IllegalArgumentException.class,()->leave.delete(12,7,5,2));
        request("LOCKED");assertThrows(IllegalArgumentException.class,()->leave.delete(12,7,5,2));
        verify(db,never()).update("DELETE FROM vacation_requests WHERE id=?",7L);
    }
}
