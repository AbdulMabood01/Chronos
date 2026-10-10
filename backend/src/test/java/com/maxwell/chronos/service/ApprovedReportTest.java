package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovedReportTest {
    @Test void reopenedApprovalCannotExportEvenWithoutAnExpiryTimestamp() {
        var db = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var access = mock(CompanyAccessService.class);
        when(db.queryForMap("SELECT * FROM timesheet_approval_periods WHERE id=?", 99L))
                .thenReturn(Map.of("user_id", 1L, "project_id", 4L, "status", "APPROVED", "opening_status", "APPROVED"));
        var reports = new ReportService(mock(TimesheetRepository.class), mock(TimesheetProjectSubmissionRepository.class),
                mock(ProjectAssignmentRepository.class), mock(VacationRequestRepository.class), mock(TimeEntryRepository.class),
                mock(UserRepository.class), mock(ProjectRepository.class), access, db);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> reports.exportApprovalPeriodPdf(99L, 1L))
                .getMessage().contains("finalized"));
    }
    @Test void pdfBrandingUsesOnlyTheOwningCompanyDisplayName() throws Exception {
        byte[] bytes;
        try(var pdf=new TimesheetPdf("TEST","October 2026","Company A Limited")){pdf.section("Employee");pdf.field("Employee","Sam Member");bytes=pdf.finish();}
        try(var document=PDDocument.load(bytes)){
            assertEquals("Company A Limited",document.getDocumentInformation().getAuthor());
            String result=new PDFTextStripper().getText(document);
            assertTrue(result.contains("Company A Limited"));assertFalse(result.contains("MAXWELL"));
        }
    }
    private final TimesheetRepository sheets = mock(TimesheetRepository.class);
    private final TimesheetProjectSubmissionRepository submissions = mock(TimesheetProjectSubmissionRepository.class);
    private final ProjectAssignmentRepository assignments = mock(ProjectAssignmentRepository.class);
    private final ReportService service = new ReportService(sheets, submissions, assignments, mock(VacationRequestRepository.class),
            mock(TimeEntryRepository.class), mock(UserRepository.class), mock(ProjectRepository.class),
            mock(CompanyAccessService.class), mock(org.springframework.jdbc.core.JdbcTemplate.class));
    private final User employee = User.builder().id(1L).employeeId("MW-0104").firstName("Jordan").lastName("Rivera")
            .jobTitle("Senior Software Engineer").build();
    private final Project project = Project.builder().id(4L).code("ATLAS").name("Enterprise Platform Modernization").build();
    private final Timesheet sheet = Timesheet.builder().id(2L).user(employee).year(2026).month(9).status(TimesheetStatus.APPROVED).build();
    private final TimesheetProjectSubmission submission = TimesheetProjectSubmission.builder().id(3L).timesheet(sheet)
            .project(project).status(TimesheetStatus.APPROVED).approvedBillRate(new BigDecimal("85"))
            .approvedBy(User.builder().firstName("Taylor").lastName("Morgan").build())
            .approvedAt(LocalDateTime.of(2026, 10, 1, 10, 30)).totalHours(BigDecimal.TEN).build();

    private byte[] export() {
        when(submissions.findById(3L)).thenReturn(Optional.of(submission));
        return service.exportProjectTimesheetPdfById(3L, 1L, false);
    }
    private String text(byte[] bytes) throws Exception {
        try (var pdf = PDDocument.load(bytes)) { return new PDFTextStripper().getText(pdf); }
    }
    @Test void preservesApprovedRateIncludingZero() throws Exception {
        submission.setApprovedBillRate(BigDecimal.ZERO);
        assertTrue(text(export()).contains("$0.00 per hour"));
        verifyNoInteractions(assignments);
    }
    @Test void missingHistoricalRateDoesNotBlockApprovedExport() throws Exception {
        submission.setApprovedBillRate(null);
        when(assignments.findByProjectIdAndUserId(4L, 1L)).thenReturn(Optional.of(ProjectAssignment.builder()
                .billRate(new BigDecimal("91.25")).build()));
        String result = text(export());
        assertTrue(result.contains("$91.25 per hour"));
        assertFalse(result.contains("999"));
    }
    @Test void usesLegacySnapshotOnlyForMatchingPrimaryProject() throws Exception {
        submission.setApprovedBillRate(null);
        sheet.setPrimaryProject(project);
        sheet.setApprovedHourlyRate(new BigDecimal("72.50"));
        assertTrue(text(export()).contains("$72.50 per hour"));
        sheet.setPrimaryProject(Project.builder().id(99L).build());
        when(assignments.findByProjectIdAndUserId(4L, 1L)).thenReturn(Optional.of(ProjectAssignment.builder()
                .billRate(new BigDecimal("88.00")).build()));
        assertTrue(text(export()).contains("$88.00 per hour"));
    }
    @Test void approvedProjectExportsEvenWhenMonthIsSubmittedAndLockedExports() throws Exception {
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        assertTrue(text(export()).contains("APPROVED TIMESHEET"));
        submission.setStatus(TimesheetStatus.LOCKED);
        assertTrue(text(export()).contains("LOCKED"));
    }
    @Test void rejectsUnapprovedAndUnauthorizedExports() {
        when(submissions.findById(3L)).thenReturn(Optional.of(submission));
        assertThrows(AccessDeniedException.class, () -> service.exportProjectTimesheetPdfById(3L, 99L, false));
        submission.setStatus(TimesheetStatus.SUBMITTED);
        assertThrows(IllegalArgumentException.class, this::export);
    }
    @Test void monthlyExportSupportsLegacyMissingRateAndProjectApprovals() throws Exception {
        when(sheets.findById(2L)).thenReturn(Optional.of(sheet));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of());
        assertTrue(text(service.exportTimesheetPdfById(2L, 1L, false)).contains("Unavailable"));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of(submission));
        String result = text(service.exportTimesheetPdfById(2L, 1L, false));
        assertTrue(result.contains("Taylor Morgan"));
        assertTrue(result.contains("$85.00 per hour"));
    }
    @Test void paginatesAllEntriesUsesSafeBrandingAndKeepsProjectScope() throws Exception {
        for (int i = 1; i <= 30; i++) {
            TimeEntry entry = TimeEntry.builder().id((long) i).timesheet(sheet).project(project)
                    .entryDate(LocalDate.of(2026, 9, i)).hours(new BigDecimal("8"))
                    .notes("Entry " + i + ": Platform delivery, integration testing and release documentation.").build();
            entry.getSessions().add(TimeEntrySession.builder().loginTime(LocalTime.of(9, 0)).logoutTime(LocalTime.of(17, 0)).build());
            sheet.getTimeEntries().add(entry);
        }
        sheet.getTimeEntries().add(TimeEntry.builder().id(100L).project(Project.builder().id(99L).code("PRIVATE").build())
                .entryDate(LocalDate.of(2026, 9, 1)).hours(BigDecimal.ONE).notes("OTHER PROJECT SECRET").build());
        submission.setTotalHours(new BigDecimal("240"));
        byte[] bytes = export();
        try (var pdf = PDDocument.load(bytes)) {
            String result = new PDFTextStripper().getText(pdf);
            assertTrue(pdf.getNumberOfPages() > 1);
            for (int i = 1; i <= 30; i++) assertTrue(result.contains("Entry " + i + ":"), "Missing entry " + i);
            assertFalse(result.contains("OTHER PROJECT SECRET"));
            assertTrue(result.contains("240.00"));
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                assertTrue(result.contains("Page " + (i + 1) + " of " + pdf.getNumberOfPages()));
                var pageText=new PDFTextStripper();pageText.setStartPage(i+1);pageText.setEndPage(i+1);
                assertTrue(pageText.getText(pdf).contains("Chronos"));
                assertFalse(pageText.getText(pdf).contains("MAXWELL"));
            }
            Path output = Path.of("target", "report-preview");
            Files.createDirectories(output);
            Files.write(output.resolve("approved-timesheet.pdf"), bytes);
            var renderer = new PDFRenderer(pdf);
            ImageIO.write(renderer.renderImageWithDPI(0, 110), "png", output.resolve("page-1.png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(pdf.getNumberOfPages() - 1, 110), "png", output.resolve("page-last.png").toFile());
        }
    }
    @Test void veryLongNotesContinueAcrossPagesWithoutDroppingTail() throws Exception {
        sheet.getTimeEntries().add(TimeEntry.builder().id(1L).project(project).entryDate(LocalDate.of(2026, 9, 1))
                .hours(BigDecimal.TEN).notes("Long detail ".repeat(800) + "FINAL-NOTE-MARKER").build());
        assertTrue(text(export()).contains("FINAL-NOTE-MARKER"));
    }
}
