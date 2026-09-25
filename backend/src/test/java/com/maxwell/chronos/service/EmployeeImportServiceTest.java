package com.maxwell.chronos.service;

import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.domain.User;
import jakarta.validation.Validation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeImportServiceTest {
    private final OnboardingService onboarding = mock(OnboardingService.class);
    private final UserRepository users = mock(UserRepository.class);

    private int run(Workbook workbook, String[][] rows) throws Exception {
        try (workbook; var factory = Validation.buildDefaultValidatorFactory(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) row.createCell(j).setCellValue(rows[i][j]);
            }
            workbook.write(output);
            return new EmployeeImportService(onboarding, users, factory.getValidator()).importEmployees(
                new MockMultipartFile("file", "employees.xlsx", "application/octet-stream", output.toByteArray()));
        }
    }

    @Test void importsBothFormatsWithReorderedHeadersAndBlankRows() throws Exception {
        for (Workbook workbook : new Workbook[]{new XSSFWorkbook(), new HSSFWorkbook()}) {
            assertEquals(1, run(workbook, new String[][]{{"Email", "Last name", "First name"}, {},
                {" ALICE@example.com ", " Smith ", " Alice "}}));
        }
        verify(onboarding, times(2)).create("Alice", "Smith", "alice@example.com");
    }

    @Test void validatesEntireSheetBeforeCreatingAnyEmployee() {
        var error = assertThrows(IllegalArgumentException.class, () -> run(new XSSFWorkbook(), new String[][]{
            {"First name", "Last name", "Email"}, {"Alice", "Smith", "alice@example.com"}, {"Bob", "", "invalid"}}));
        assertTrue(error.getMessage().contains("Row 3"));
        verifyNoInteractions(onboarding);
    }

    @Test void rejectsDuplicateEmailsIgnoringCase() {
        assertThrows(IllegalArgumentException.class, () -> run(new XSSFWorkbook(), new String[][]{
            {"First name", "Last name", "Email"}, {"Alice", "Smith", "alice@example.com"}, {"Bob", "Smith", "ALICE@example.com"}}));
        verifyNoInteractions(onboarding);
    }

    @Test void rejectsExistingEmployees() {
        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(new User()));
        assertThrows(IllegalArgumentException.class, () -> run(new XSSFWorkbook(), new String[][]{
            {"First name", "Last name", "Email"}, {"Alice", "Smith", "alice@example.com"}}));
        verifyNoInteractions(onboarding);
    }

    @Test void rejectsMissingHeadersAndEmptySheets() {
        assertThrows(IllegalArgumentException.class, () -> run(new XSSFWorkbook(), new String[][]{{"Name"}}));
        assertThrows(IllegalArgumentException.class, () -> run(new XSSFWorkbook(), new String[][]{{"First name", "Last name", "Email"}}));
        verifyNoInteractions(onboarding);
    }
}
