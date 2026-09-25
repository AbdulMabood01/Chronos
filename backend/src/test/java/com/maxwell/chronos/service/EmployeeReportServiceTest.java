package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.EmployeeReportService.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeReportServiceTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    UserRepository users = mock(UserRepository.class);
    EmployeeReportService service = new EmployeeReportService(db, users, mock(EmailAlertService.class));
    User user = User.builder().id(7L).email("user@example.com").role(UserRole.ADMIN).isActive(true).build();
    UUID id = UUID.randomUUID();
    @BeforeEach void setup() { when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user)); }
    Submission submission(boolean anonymous) { return new Submission(Category.SEXUAL_HARASSMENT, "Subject", "Description", null, null, null, null, anonymous, true); }
    @Test void anonymousReportAndSubmissionHistoryHaveNoAccountLink() {
        service.submit(user.getEmail(), submission(true), List.of());
        verify(db).update(contains("INSERT INTO employee_reports("), any(UUID.class), eq("SEXUAL_HARASSMENT"), eq("Subject"), eq("Description"), isNull(), isNull(), isNull(), isNull(), eq(true), isNull());
        verify(db).update(contains("INSERT INTO employee_report_history"), any(UUID.class), isNull(), eq("SUBMITTED"), isNull(), isNull(), isNull(), any(UUID.class));
    }
    @Test void identifiedReportLinksOnlyWhenRequested() {
        service.submit(user.getEmail(), submission(false), List.of());
        verify(db).update(contains("INSERT INTO employee_reports("), any(UUID.class), eq("SEXUAL_HARASSMENT"), eq("Subject"), eq("Description"), isNull(), isNull(), isNull(), isNull(), eq(false), eq(7L));
    }
    @Test void rejectsUnsupportedOrOversizedAttachmentsBeforeInsert() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(user.getEmail(), submission(true), List.of(new MockMultipartFile("attachments", "file.html", "text/html", "bad".getBytes()))));
        assertThrows(IllegalArgumentException.class, () -> service.submit(user.getEmail(), submission(true), List.of(new MockMultipartFile("attachments", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]))));
        verifyNoInteractions(db);
    }
    @Test void confidentialReadStripsAnonymousReporterAndAuditsAccess() {
        Map<String,Object> row = new HashMap<>(Map.of("id",id,"anonymous",true,"reporter_id",7L));
        when(db.queryForList("SELECT * FROM employee_reports WHERE id=?", id)).thenReturn(List.of(row));
        var result = service.detail(user.getEmail(), id);
        assertFalse(result.containsKey("reporter_id")); assertFalse(result.containsKey("reporter"));
        verify(db).update(contains("INSERT INTO employee_report_history"), eq(id), eq(7L), eq("VIEWED"), isNull(), isNull(), isNull(), eq(id));
    }
    @Test void validatesWorkflowAndRequiresResolution() {
        Map<String,Object> row = new HashMap<>(Map.of("status","SUBMITTED"));
        when(db.queryForList("SELECT * FROM employee_reports WHERE id=? FOR UPDATE", id)).thenReturn(List.of(row));
        assertThrows(IllegalArgumentException.class, () -> service.review(user.getEmail(), id, new Review(Status.CLOSED,null,null,null)));
        service.review(user.getEmail(), id, new Review(Status.UNDER_REVIEW,"Review started",null,null));
        verify(db).update("UPDATE employee_reports SET status=?,updated_at=clock_timestamp() WHERE id=?", "UNDER_REVIEW", id);
        row.put("status","INVESTIGATION");
        assertThrows(IllegalArgumentException.class, () -> service.review(user.getEmail(), id, new Review(Status.RESOLVED,null,null,null)));
        service.review(user.getEmail(), id, new Review(Status.RESOLVED,null,"Interviewed witnesses","Resolved with follow-up"));
        row.put("status","CLOSED");
        assertThrows(IllegalArgumentException.class, () -> service.review(user.getEmail(), id, new Review(Status.CLOSED,"Edit",null,null)));
    }
    @Test void revokedRoleCannotAccessServiceDirectly() {
        user.setRole(UserRole.PROJECT_ADMIN);
        assertThrows(AccessDeniedException.class, () -> service.detail(user.getEmail(), id));
        assertThrows(AccessDeniedException.class, () -> service.download(user.getEmail(), id, UUID.randomUUID()));
        verifyNoInteractions(db);
    }
    @Test void employeeReadsAreScopedToOwnerAndExcludeAnonymousReports() {
        user.setRole(UserRole.EMPLOYEE);
        service.mine(user.getEmail(), 0);
        verify(db).queryForList(contains("WHERE reporter_id=? AND NOT anonymous"), eq(7L), eq(0));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.myDetail(user.getEmail(), id));
        verify(db).queryForList(contains("WHERE id=? AND reporter_id=? AND NOT anonymous"), eq(id), eq(7L));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void inactiveUserCannotReadOwnReports() {
        user.setIsActive(false);
        assertThrows(AccessDeniedException.class, () -> service.mine(user.getEmail(), 0));
        assertThrows(AccessDeniedException.class, () -> service.myDetail(user.getEmail(), id));
        verifyNoInteractions(db);
    }
    @Test void sharingRequiresExplicitReviewChoice() {
        when(db.queryForList("SELECT * FROM employee_reports WHERE id=? FOR UPDATE", id))
            .thenReturn(new ArrayList<>(List.of(new HashMap<>(Map.of("status", "SUBMITTED")))));
        service.review(user.getEmail(), id, new Review(Status.UNDER_REVIEW, "Private", "Shared action", null, true));
        verify(db).update(contains("status,employee_visible)"), eq(id), eq(7L), anyString(), eq("Private"), eq("Shared action"), isNull(), eq(id));
    }
    @Test void attachmentsAreScopedToReportAndDownloadIsAudited() {
        UUID file = UUID.randomUUID();
        when(db.queryForList("SELECT a.filename,a.content,a.privacy_processed,r.anonymous FROM employee_report_attachments a JOIN employee_reports r ON r.id=a.report_id WHERE a.id=? AND a.report_id=?", file, id))
            .thenReturn(List.of(Map.of("filename","evidence.pdf","content",new byte[]{1,2})));
        assertArrayEquals(new byte[]{1,2}, service.download(user.getEmail(), id, file).content());
        verify(db).update(contains("INSERT INTO employee_report_history"), eq(id), eq(7L), eq("ATTACHMENT_DOWNLOADED"), eq(file.toString()), isNull(), isNull(), eq(id));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.download(user.getEmail(), UUID.randomUUID(), file));
    }
}
