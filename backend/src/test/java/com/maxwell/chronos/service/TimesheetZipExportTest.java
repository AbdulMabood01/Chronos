package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimesheetZipExportTest {
    @Test void exportsSelectedEmployeesAsPdfsWithProjectTotalsAndAmounts() throws Exception {
        var sheets = mock(TimesheetRepository.class);
        var submissions = mock(TimesheetProjectSubmissionRepository.class);
        var assignments = mock(ProjectAssignmentRepository.class);
        var service = new ReportService(sheets, submissions, assignments, mock(VacationRequestRepository.class));
        var oldSheet = Timesheet.builder().id(1L).year(2026).month(9).status(TimesheetStatus.APPROVED)
                .user(User.builder().id(10L).role(UserRole.EMPLOYEE).firstName("Old").lastName("Record").build()).build();
        var zeroSheet = Timesheet.builder().id(2L).year(2026).month(9).status(TimesheetStatus.APPROVED)
                .user(User.builder().id(20L).role(UserRole.EMPLOYEE).firstName("Zero").lastName("Rate").build()).build();
        var draftOnly = Timesheet.builder().id(3L).year(2026).month(9).status(TimesheetStatus.DRAFT)
                .user(User.builder().id(30L).role(UserRole.EMPLOYEE).firstName("Draft").lastName("Only").build()).build();
        var unselected = Timesheet.builder().id(4L).user(User.builder().id(40L).role(UserRole.EMPLOYEE).build()).build();
        var atlas = Project.builder().id(100L).code("ATLAS").name("Migration").build();
        var beta = Project.builder().id(101L).code("BETA").name("Buildout").build();
        var hold = Project.builder().id(102L).code("HOLD").name("Rejected Work").build();
        var zeus = Project.builder().id(200L).code("ZEUS").name("Support").build();
        oldSheet.getTimeEntries().add(TimeEntry.builder().project(atlas).entryDate(LocalDate.of(2026, 9, 1)).hours(BigDecimal.TEN).build());
        oldSheet.getTimeEntries().add(TimeEntry.builder().project(beta).entryDate(LocalDate.of(2026, 9, 2)).hours(new BigDecimal("3")).build());
        oldSheet.getTimeEntries().add(TimeEntry.builder().project(hold).entryDate(LocalDate.of(2026, 9, 3)).hours(new BigDecimal("7")).notes("Rejected project should not export").build());
        zeroSheet.getTimeEntries().add(TimeEntry.builder().project(zeus).entryDate(LocalDate.of(2026, 9, 1)).hours(new BigDecimal("5")).build());
        when(sheets.findByYearAndMonth(2026, 9)).thenReturn(List.of(oldSheet, zeroSheet, draftOnly, unselected));
        when(submissions.findByTimesheetId(1L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder()
                        .timesheet(oldSheet).project(atlas).status(TimesheetStatus.APPROVED).totalHours(BigDecimal.TEN).build(),
                TimesheetProjectSubmission.builder()
                        .timesheet(oldSheet).project(beta).status(TimesheetStatus.APPROVED).totalHours(new BigDecimal("3"))
                        .approvedBillRate(new BigDecimal("100")).build(),
                TimesheetProjectSubmission.builder()
                        .timesheet(oldSheet).project(hold).status(TimesheetStatus.REJECTED).totalHours(new BigDecimal("7"))
                        .approvedBillRate(new BigDecimal("200")).build()));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of(TimesheetProjectSubmission.builder()
                .timesheet(zeroSheet).project(zeus).status(TimesheetStatus.APPROVED).totalHours(new BigDecimal("5"))
                .approvedBillRate(new BigDecimal("75")).build()));
        when(submissions.findByTimesheetId(3L)).thenReturn(List.of(TimesheetProjectSubmission.builder()
                .timesheet(draftOnly).project(zeus).status(TimesheetStatus.DRAFT).totalHours(new BigDecimal("5")).build()));
        when(assignments.findByProjectIdAndUserId(100L, 10L)).thenReturn(Optional.of(ProjectAssignment.builder()
                .billRate(new BigDecimal("80")).build()));
        byte[] bytes = service.exportTimesheets(2026, 9, List.of(10L, 20L, 30L));
        var names = new ArrayList<String>();
        var texts = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                try (var pdf = PDDocument.load(zip.readAllBytes())) {
                    texts.add(new PDFTextStripper().getText(pdf));
                }
            }
        }
        assertEquals(List.of("Old_Record,September,2026.pdf", "Zero_Rate,September,2026.pdf"), names);
        assertTrue(texts.get(0).contains("PROJECT SUMMARY"));
        assertTrue(texts.get(0).contains("ATLAS - Migration"));
        assertTrue(texts.get(0).contains("BETA - Buildout"));
        assertTrue(texts.get(0).contains("10.00"));
        assertTrue(texts.get(0).contains("$80.00/hr"));
        assertTrue(texts.get(0).contains("$800.00"));
        assertTrue(texts.get(0).contains("$300.00"));
        assertFalse(texts.get(0).contains("HOLD - Rejected Work"));
        assertFalse(texts.get(0).contains("Rejected project should not export"));
        assertTrue(texts.get(1).contains("ZEUS - Support"));
        assertTrue(texts.get(1).contains("$75.00 per hour"));
        assertTrue(texts.get(1).contains("$375.00"));
        verify(submissions, never()).findByTimesheetId(4L);
        verify(assignments, atLeastOnce()).findByProjectIdAndUserId(100L, 10L);
    }

    @Test void exportsOnlySelectedApprovedProjectSubmissions() throws Exception {
        var sheets = mock(TimesheetRepository.class);
        var submissions = mock(TimesheetProjectSubmissionRepository.class);
        var assignments = mock(ProjectAssignmentRepository.class);
        var service = new ReportService(sheets, submissions, assignments, mock(VacationRequestRepository.class));
        var user = User.builder().id(10L).role(UserRole.EMPLOYEE).firstName("Project").lastName("Member").build();
        var sheet = Timesheet.builder().id(1L).year(2026).month(9).status(TimesheetStatus.SUBMITTED).user(user).build();
        var approvedProject = Project.builder().id(100L).code("GOOD").name("Approved").build();
        var draftProject = Project.builder().id(200L).code("DRAFT").name("Ignored").build();
        sheet.getTimeEntries().add(TimeEntry.builder().project(approvedProject).entryDate(LocalDate.of(2026, 9, 1)).hours(BigDecimal.TEN).build());
        sheet.getTimeEntries().add(TimeEntry.builder().project(draftProject).entryDate(LocalDate.of(2026, 9, 2)).hours(BigDecimal.ONE).notes("Should not export").build());
        var approved = TimesheetProjectSubmission.builder().id(50L).timesheet(sheet).project(approvedProject)
                .status(TimesheetStatus.APPROVED).totalHours(BigDecimal.TEN).approvedBillRate(new BigDecimal("25")).build();
        var draft = TimesheetProjectSubmission.builder().id(60L).timesheet(sheet).project(draftProject)
                .status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ONE).approvedBillRate(new BigDecimal("100")).build();
        when(submissions.findById(50L)).thenReturn(Optional.of(approved));
        when(submissions.findById(60L)).thenReturn(Optional.of(draft));

        byte[] bytes = service.exportProjectTimesheets(List.of(50L, 60L));
        var names = new ArrayList<String>();
        var texts = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                try (var pdf = PDDocument.load(zip.readAllBytes())) {
                    texts.add(new PDFTextStripper().getText(pdf));
                }
            }
        }

        assertEquals(List.of("GOOD_Project_Member,September,2026.pdf"), names);
        assertTrue(texts.getFirst().contains("GOOD - Approved"));
        assertFalse(texts.getFirst().contains("DRAFT - Ignored"));
        assertFalse(texts.getFirst().contains("Should not export"));
        verifyNoInteractions(assignments);
    }
}
