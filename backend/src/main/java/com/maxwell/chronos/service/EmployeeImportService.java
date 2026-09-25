package com.maxwell.chronos.service;

import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.web.OnboardingController.EmployeeRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeImportService {
    private final OnboardingService onboarding;
    private final UserRepository users;
    private final Validator validator;

    @Transactional
    public int importEmployees(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new IllegalArgumentException("Choose an Excel file up to 5 MB.");
        List<EmployeeRequest> employees = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("The workbook has no sheets.");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> columns = new HashMap<>();
            if (header != null) for (Cell cell : header) {
                String name = value(cell).toLowerCase(Locale.ROOT);
                if (columns.put(name, cell.getColumnIndex()) != null && List.of("first name", "last name", "email").contains(name))
                    throw new IllegalArgumentException("Duplicate column: " + name);
            }
            if (!columns.keySet().containsAll(List.of("first name", "last name", "email")))
                throw new IllegalArgumentException("The first row must contain First name, Last name, and Email columns.");
            if (sheet.getLastRowNum() > 1000) throw new IllegalArgumentException("Import up to 1,000 rows at a time.");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                EmployeeRequest employee = new EmployeeRequest();
                employee.firstName = value(row.getCell(columns.get("first name")));
                employee.lastName = value(row.getCell(columns.get("last name")));
                employee.email = value(row.getCell(columns.get("email"))).toLowerCase(Locale.ROOT);
                if (employee.firstName.isEmpty() && employee.lastName.isEmpty() && employee.email.isEmpty()) continue;
                String prefix = "Row " + (i + 1) + ": ";
                var violations = validator.validate(employee);
                if (!violations.isEmpty()) throw new IllegalArgumentException(prefix + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().findFirst().orElse("Invalid employee"));
                if (!emails.add(employee.email)) throw new IllegalArgumentException(prefix + "duplicate email in spreadsheet.");
                if (users.findByEmailIgnoreCase(employee.email).isPresent())
                    throw new IllegalArgumentException(prefix + "an employee with this email already exists.");
                employees.add(employee);
            }
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Unable to read this workbook. Upload a valid, unprotected .xlsx or .xls file."); }
        if (employees.isEmpty()) throw new IllegalArgumentException("The spreadsheet contains no employees.");
        for (EmployeeRequest employee : employees) onboarding.create(employee.firstName, employee.lastName, employee.email);
        return employees.size();
    }

    private static String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() != CellType.STRING)
            throw new IllegalArgumentException("Row " + (cell.getRowIndex() + 1) + ": use plain text for names and email; formulas are not supported.");
        return cell.getStringCellValue().trim();
    }
}
