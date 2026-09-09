package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.TimeEntry;
import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.domain.VacationRequest;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.VacationRequestRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {
    private final TimesheetRepository timesheetRepository;
    private final TimesheetProjectSubmissionRepository projectSubmissionRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final VacationRequestRepository vacationRequestRepository;

    public byte[] exportTimesheets(int year, int month, List<Long> userIds) {
        List<Timesheet> timesheets = filterTimesheets(timesheetRepository.findByYearAndMonth(year, month), userIds);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> entryNames = new HashSet<>();
            for (Timesheet ts : timesheets) {
                zip.putNextEntry(new ZipEntry(buildUniqueTimesheetFilename(ts, entryNames)));
                zip.write(buildTimesheetWorkbook(ts));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] exportVacationRequests(int year) {
        List<VacationRequest> requests = vacationRequestRepository.findByStartDateBetween(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vacation Requests " + year);
            String[] columns = {"Employee", "Start Date", "End Date", "Type", "Hours", "Status", "Submitted At"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int rowIdx = 1;
            for (VacationRequest vr : requests) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(vr.getUser().getFullName());
                row.createCell(1).setCellValue(vr.getStartDate().toString());
                row.createCell(2).setCellValue(vr.getEndDate().toString());
                row.createCell(3).setCellValue(vr.getVacationType().name());
                row.createCell(4).setCellValue(vr.getHours() != null ? vr.getHours().doubleValue() : 0);
                row.createCell(5).setCellValue(vr.getStatus().name());
                row.createCell(6).setCellValue(vr.getSubmittedAt() != null ? vr.getSubmittedAt().toString() : "");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] exportTimesheetById(Long timesheetId, Long requestingUserId, boolean isAdmin) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!isAdmin && !timesheet.getUser().getId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Not authorized to export this timesheet");
        }

        return buildTimesheetWorkbook(timesheet);
    }

    public byte[] exportTimesheetPdfById(Long timesheetId, Long requestingUserId, boolean isAdmin) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!isAdmin && !timesheet.getUser().getId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Not authorized to export this timesheet");
        }
        if (!com.maxwell.chronos.enums.TimesheetStatus.APPROVED.equals(timesheet.getStatus())
                && !com.maxwell.chronos.enums.TimesheetStatus.LOCKED.equals(timesheet.getStatus())) {
            throw new IllegalArgumentException("Timesheet must be approved before PDF export");
        }

        return buildTimesheetPdf(timesheet);
    }

    public byte[] exportProjectTimesheetPdfById(Long submissionId, Long requestingUserId, boolean isAdmin) {
        TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
        Timesheet timesheet = submission.getTimesheet();

        if (!isAdmin && !timesheet.getUser().getId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Not authorized to export this timesheet");
        }
        if (!submission.isPdfExportEligible()) {
            throw new IllegalArgumentException("Timesheet must be approved before PDF export");
        }

        return buildTimesheetPdf(timesheet, submission);
    }

    private byte[] buildTimesheetPdf(Timesheet timesheet) {
        return buildTimesheetPdf(timesheet, null);
    }

    private byte[] buildTimesheetPdf(Timesheet timesheet, TimesheetProjectSubmission submission) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 742;
                TimesheetStatus status = submission != null ? submission.getStatus() : timesheet.getStatus();
                String projectLabel = submission != null
                        ? submission.getProject().getCode() + " - " + submission.getProject().getName()
                        : timesheet.getPrimaryProject() != null ? timesheet.getPrimaryProject().getCode() + " - " + timesheet.getPrimaryProject().getName() : "";
                y = writeLine(content, "Approved Timesheet", 50, y, 18, true);
                y -= 10;
                y = writeLine(content, "Employee: " + timesheet.getUser().getFullName(), 50, y, 11, false);
                y = writeLine(content, "Designation: " + safe(timesheet.getUser().getJobTitle()), 50, y, 11, false);
                y = writeLine(content, "Project: " + projectLabel, 50, y, 11, false);
                y = writeLine(content, "Bill Rate: $" + resolveBillRate(timesheet, submission).stripTrailingZeros().toPlainString() + " per hour", 50, y, 11, false);
                y = writeLine(content, "Period: " + displayMonth(YearMonth.of(timesheet.getYear(), timesheet.getMonth()).getMonth()) + " " + timesheet.getYear(), 50, y, 11, false);
                y = writeLine(content, "Status: " + status, 50, y, 11, false);
                y = writeLine(content, "Approver: " + approverName(timesheet, submission), 50, y, 11, false);
                y = writeLine(content, "Approval Date: " + approvalDate(timesheet, submission), 50, y, 11, false);
                y -= 12;
                y = writeLine(content, "Date        Project      Hours   Login/Logout", 50, y, 11, true);
                y -= 4;

                List<TimeEntry> entries = timesheet.getTimeEntries().stream()
                        .filter(entry -> submission == null
                                || (entry.getProject() != null && entry.getProject().getId().equals(submission.getProject().getId())))
                        .sorted(Comparator.comparing(TimeEntry::getEntryDate))
                        .toList();
                for (TimeEntry entry : entries) {
                    String sessions = entry.getSessions().stream()
                            .map(session -> session.getLoginTime() + "-" + session.getLogoutTime())
                            .collect(Collectors.joining(", "));
                    String project = entry.getProject() != null ? entry.getProject().getCode() : "";
                    y = writeLine(content, entry.getEntryDate() + "  " + pad(project, 10) + "  "
                            + entry.getHours() + "    " + sessions, 50, y, 10, false);
                    if (y < 70) {
                        break;
                    }
                }
                y -= 12;
                BigDecimal totalHours = submission != null ? submission.getTotalHours() : timesheet.getTotalHours();
                writeLine(content, "Total Hours: " + (totalHours != null ? totalHours : BigDecimal.ZERO), 50, y, 12, true);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String approverName(Timesheet timesheet, TimesheetProjectSubmission submission) {
        if (submission != null) {
            return submission.getApprovedBy() != null ? submission.getApprovedBy().getFullName() : "";
        }
        return timesheet.getApprovedBy() != null ? timesheet.getApprovedBy().getFullName() : "";
    }

    private String approvalDate(Timesheet timesheet, TimesheetProjectSubmission submission) {
        if (submission != null) {
            return submission.getApprovedAt() != null ? submission.getApprovedAt().toLocalDate().toString() : "";
        }
        return timesheet.getApprovedAt() != null ? timesheet.getApprovedAt().toLocalDate().toString() : "";
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, int fontSize, boolean bold) throws IOException {
        content.beginText();
        content.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
        return y - fontSize - 6;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").replaceAll("[^\\x20-\\x7E]", "");
    }

    private String pad(String value, int size) {
        String safeValue = safe(value);
        return safeValue.length() >= size ? safeValue : safeValue + " ".repeat(size - safeValue.length());
    }

    private BigDecimal resolveBillRate(Timesheet timesheet, TimesheetProjectSubmission submission) {
        Long projectId = submission != null
                ? submission.getProject().getId()
                : timesheet.getPrimaryProject() != null ? timesheet.getPrimaryProject().getId() : null;
        if (projectId != null) {
            BigDecimal projectRate = projectAssignmentRepository.findByProjectIdAndUserId(projectId, timesheet.getUser().getId())
                    .map(assignment -> assignment.getBillRate() != null ? assignment.getBillRate() : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
            if (projectRate.compareTo(BigDecimal.ZERO) > 0) {
                return projectRate;
            }
        }
        if (timesheet.getUser().getHourlyRate() != null) {
            return timesheet.getUser().getHourlyRate();
        }
        return BigDecimal.ZERO;
    }

    private byte[] buildTimesheetWorkbook(Timesheet timesheet) {
        InputStream templateStream = getClass().getResourceAsStream("/templates/timesheet-template.xlsx");
        if (templateStream == null) {
            throw new IllegalStateException("Timesheet export template not found");
        }

        try (InputStream template = templateStream;
             XSSFWorkbook workbook = new XSSFWorkbook(template);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            workbook.setSheetName(workbook.getSheetIndex(sheet), "Timesheet");
            sheet.setDisplayGridlines(false);
            sheet.setPrintGridlines(false);
            sheet.setHorizontallyCenter(true);
            sheet.setMargin(Sheet.LeftMargin, 0.7);
            sheet.setMargin(Sheet.RightMargin, 0.7);
            sheet.setMargin(Sheet.TopMargin, 0.75);
            sheet.setMargin(Sheet.BottomMargin, 0.75);
            sheet.setMargin(Sheet.HeaderMargin, 0.3);
            sheet.setMargin(Sheet.FooterMargin, 0.3);
            PrintSetup printSetup = sheet.getPrintSetup();
            printSetup.setLandscape(false);
            printSetup.setFitWidth((short) 1);
            printSetup.setFitHeight((short) 0);

            BigDecimal rate = resolveBillRate(timesheet, null).setScale(2, RoundingMode.HALF_UP);
            YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
            Map<LocalDate, TimeEntry> entriesByDate = timesheet.getTimeEntries().stream()
                    .collect(Collectors.toMap(TimeEntry::getEntryDate, entry -> entry, (left, right) -> left));
            Map<LocalDate, String> vacationStatusByDate = getVacationStatusByDate(timesheet, period);
            CellStyle vacationDateStyle = cloneWithFill(workbook, cellAt(sheet, 13, 1).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationValueStyle = cloneWithFill(workbook, cellAt(sheet, 13, 2).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationHoursStyle = cloneWithFill(workbook, cellAt(sheet, 13, 6).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationNoteStyle = cloneWithFill(workbook, cellAt(sheet, 25, 9).getCellStyle(), IndexedColors.LIGHT_GREEN);

            setCellValue(sheet, 1, 1, "TIMESHEET for " + displayMonth(period.getMonth()) + " (" + period.getYear() + ")");
            setCellValue(sheet, 1, 9, "Pay rate: $" + rate.stripTrailingZeros().toPlainString() + " per hour");
            setRichLabelCell(workbook, sheet, 5, 1, "Name", ": " + timesheet.getUser().getFullName(), 12, IndexedColors.BLUE);
            setRichLabelCell(workbook, sheet, 6, 1, "Last 4 digits of SS", ":", 12, IndexedColors.BLUE);
            setCellValue(sheet, 6, 2, timesheet.getUser().getSsnLast4());
            setRichLabelCell(workbook, sheet, 5, 4, "Name", ": Syed Hussain ", 12, IndexedColors.BLUE);
            setRichLabelCell(workbook, sheet, 6, 4, "Enter Manager's Phone No", ": (872) 999-2576", 12, IndexedColors.BLUE);
            setCellValue(sheet, 8, 1, 40);
            setCellValue(sheet, 11, 1, java.sql.Date.valueOf(period.atDay(1)));
            setCellValue(sheet, 11, 4, java.sql.Date.valueOf(period.atEndOfMonth()));

            BigDecimal totalHours = BigDecimal.ZERO;
            for (int day = 1; day <= 31; day++) {
                LocalDate date = day <= period.lengthOfMonth() ? period.atDay(day) : null;
                TimeEntry entry = entriesByDate.get(date);
                String vacationStatus = date != null ? vacationStatusByDate.getOrDefault(date, "") : "";
                boolean approvedVacation = isApprovedVacationStatus(vacationStatus);
                BigDecimal hours = entry != null && entry.getHours() != null ? entry.getHours() : BigDecimal.ZERO;
                if (approvedVacation) {
                    hours = BigDecimal.ZERO;
                }
                if (date != null) {
                    totalHours = totalHours.add(hours);
                }

                int rowNumber = 12 + day;
                setCellValue(sheet, rowNumber, 1, date != null ? java.sql.Date.valueOf(date) : null);
                clearCell(sheet, rowNumber, 2);
                clearCell(sheet, rowNumber, 3);
                clearCell(sheet, rowNumber, 4);
                clearCell(sheet, rowNumber, 5);
                setCellValue(sheet, rowNumber, 6, date != null ? hours.doubleValue() : null);
                clearCell(sheet, rowNumber, 9);

                if (approvedVacation) {
                    cellAt(sheet, rowNumber, 1).setCellStyle(vacationDateStyle);
                    for (int column = 2; column <= 5; column++) {
                        cellAt(sheet, rowNumber, column).setCellStyle(vacationValueStyle);
                    }
                    cellAt(sheet, rowNumber, 6).setCellStyle(vacationHoursStyle);
                }

                String note = entry != null && entry.getNotes() != null ? entry.getNotes() : "";
                if (approvedVacation) {
                    note = vacationStatus;
                }
                if (!note.isBlank()) {
                    setCellValue(sheet, rowNumber, 9, note);
                    if (approvedVacation) {
                        cellAt(sheet, rowNumber, 9).setCellStyle(vacationNoteStyle);
                    }
                }
            }

            setCellValue(sheet, 8, 4, totalHours.doubleValue());
            setRichLabelCell(workbook, sheet, 25, 9, "Note", ": No hours days are either weekends, holidays, or approved vacation days", 16, IndexedColors.BLACK);
            workbook.setForceFormulaRecalculation(true);
            sheet.setForceFormulaRecalculation(true);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setSampleColumnWidths(Sheet sheet) {
        double[] widths = {7.9, 17.7, 17.7, 17.7, 19.2, 17.7, 17.7, 3.7, 7.9, 71.6};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, (int) (widths[i] * 256));
        }
    }

    private void applyDefaultRows(Sheet sheet, int rowCount) {
        for (int i = 0; i < rowCount; i++) {
            Row row = sheet.createRow(i);
            row.setHeightInPoints(i < 12 ? 36 : 30);
        }
    }

    private org.apache.poi.ss.usermodel.Cell createStyledCell(Sheet sheet, int rowIndex, int columnIndex, Object value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        var cell = row.createCell(columnIndex);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof java.util.Date date) {
            cell.setCellValue(date);
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }
        cell.setCellStyle(style);
        return cell;
    }

    private Cell cellAt(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        return cell;
    }

    private void setCellValue(Sheet sheet, int rowIndex, int columnIndex, Object value) {
        Cell cell = cellAt(sheet, rowIndex, columnIndex);
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            cell.setCellFormula(null);
        }
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof java.util.Date date) {
            cell.setCellValue(date);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void clearCell(Sheet sheet, int rowIndex, int columnIndex) {
        Cell cell = cellAt(sheet, rowIndex, columnIndex);
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            cell.setCellFormula(null);
        }
        cell.setBlank();
    }

    private void setRichLabelCell(XSSFWorkbook workbook, Sheet sheet, int rowIndex, int columnIndex,
                                  String label, String value, int fontSize, IndexedColors color) {
        Cell cell = cellAt(sheet, rowIndex, columnIndex);
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            cell.setCellFormula(null);
        }

        XSSFFont labelFont = workbook.createFont();
        labelFont.setFontName("Arial");
        labelFont.setFontHeightInPoints((short) fontSize);
        labelFont.setColor(color.getIndex());
        labelFont.setBold(true);

        XSSFFont valueFont = workbook.createFont();
        valueFont.setFontName("Arial");
        valueFont.setFontHeightInPoints((short) fontSize);
        valueFont.setColor(color.getIndex());

        XSSFRichTextString richText = new XSSFRichTextString(label + value);
        richText.applyFont(0, label.length(), labelFont);
        richText.applyFont(label.length(), label.length() + value.length(), valueFont);
        cell.setCellValue(richText);
    }

    private CellStyle cloneWithFill(XSSFWorkbook workbook, CellStyle baseStyle, IndexedColors fill) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String buildTimesheetFilename(Timesheet timesheet) {
        YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        String safeName = timesheet.getUser().getFullName().trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safeName.isBlank()) {
            safeName = "employee_" + timesheet.getUser().getId();
        }
        return safeName + "," + displayMonth(period.getMonth()) + "," + period.getYear() + ".xlsx";
    }

    private String buildUniqueTimesheetFilename(Timesheet timesheet, Set<String> entryNames) {
        String filename = buildTimesheetFilename(timesheet);
        if (entryNames.add(filename)) {
            return filename;
        }

        String baseName = filename.substring(0, filename.length() - ".xlsx".length());
        String suffix = timesheet.getUser().getEmployeeId() != null && !timesheet.getUser().getEmployeeId().isBlank()
                ? timesheet.getUser().getEmployeeId()
                : "timesheet_" + timesheet.getId();
        String candidate = baseName + "_" + suffix.replaceAll("[^A-Za-z0-9]+", "_") + ".xlsx";

        int counter = 2;
        while (!entryNames.add(candidate)) {
            candidate = baseName + "_" + suffix.replaceAll("[^A-Za-z0-9]+", "_") + "_" + counter + ".xlsx";
            counter++;
        }

        return candidate;
    }

    private String displayMonth(Month month) {
        String lower = month.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static class WorkbookStyles {
        final CellStyle title;
        final CellStyle company;
        final CellStyle section;
        final CellStyle blueLabel;
        final CellStyle date;
        final CellStyle tableHeader;
        final CellStyle value;
        final CellStyle hours;
        final CellStyle tableDate;
        final CellStyle tableValue;
        final CellStyle tableHours;
        final CellStyle numberLarge;
        final CellStyle note;
        final CellStyle vacationDate;
        final CellStyle vacationValue;
        final CellStyle vacationHours;
        final CellStyle vacationTableDate;
        final CellStyle vacationTableValue;
        final CellStyle vacationTableHours;
        final CellStyle vacationNote;
        final CellStyle total;
        final CellStyle currency;

        WorkbookStyles(XSSFWorkbook workbook) {
            DataFormat dataFormat = workbook.createDataFormat();
            Font titleFont = font(workbook, true, 28, IndexedColors.WHITE);
            Font companyFont = font(workbook, true, 36, IndexedColors.WHITE);
            Font sectionFont = font(workbook, true, 12, IndexedColors.WHITE);
            Font blueFont = font(workbook, false, 12, IndexedColors.BLUE);
            Font normalFont = font(workbook, false, 12, IndexedColors.GREY_80_PERCENT);
            Font boldBlueFont = font(workbook, true, 12, IndexedColors.BLUE);
            Font noteFont = font(workbook, false, 16, IndexedColors.BLACK);

            title = style(workbook, titleFont, IndexedColors.BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            company = style(workbook, companyFont, IndexedColors.BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            section = style(workbook, sectionFont, IndexedColors.BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            blueLabel = style(workbook, blueFont, IndexedColors.PALE_BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            date = style(workbook, boldBlueFont, null, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            date.setDataFormat(dataFormat.getFormat("m/d/yyyy"));
            tableHeader = borderedStyle(workbook, sectionFont, IndexedColors.BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            value = style(workbook, normalFont, null, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            hours = style(workbook, blueFont, null, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            hours.setDataFormat(dataFormat.getFormat("0.00"));
            tableDate = borderedStyle(workbook, boldBlueFont, null, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            tableDate.setDataFormat(dataFormat.getFormat("m/d/yyyy"));
            tableValue = borderedStyle(workbook, normalFont, null, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            tableHours = borderedStyle(workbook, blueFont, null, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            tableHours.setDataFormat(dataFormat.getFormat("0.00"));
            numberLarge = style(workbook, boldBlueFont, IndexedColors.PALE_BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            numberLarge.setDataFormat(dataFormat.getFormat("0.00"));
            note = style(workbook, noteFont, null, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            note.setWrapText(true);
            vacationDate = style(workbook, boldBlueFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            vacationDate.setDataFormat(dataFormat.getFormat("m/d/yyyy"));
            vacationValue = style(workbook, normalFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            vacationHours = style(workbook, blueFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            vacationHours.setDataFormat(dataFormat.getFormat("0.00"));
            vacationTableDate = borderedStyle(workbook, boldBlueFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            vacationTableDate.setDataFormat(dataFormat.getFormat("m/d/yyyy"));
            vacationTableValue = borderedStyle(workbook, normalFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            vacationTableHours = borderedStyle(workbook, blueFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            vacationTableHours.setDataFormat(dataFormat.getFormat("0.00"));
            vacationNote = style(workbook, noteFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            vacationNote.setWrapText(true);
            total = style(workbook, boldBlueFont, IndexedColors.PALE_BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            currency = style(workbook, boldBlueFont, IndexedColors.PALE_BLUE, HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            currency.setDataFormat(dataFormat.getFormat("$#,##0.00"));
        }

        private static Font font(XSSFWorkbook workbook, boolean bold, int size, IndexedColors color) {
            Font font = workbook.createFont();
            font.setFontName("Arial");
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(color.getIndex());
            return font;
        }

        private static CellStyle style(XSSFWorkbook workbook, Font font, IndexedColors fill, HorizontalAlignment horizontal, VerticalAlignment vertical) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(horizontal);
            style.setVerticalAlignment(vertical);
            if (fill != null) {
                style.setFillForegroundColor(fill.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        private static CellStyle borderedStyle(XSSFWorkbook workbook, Font font, IndexedColors fill,
                                               HorizontalAlignment horizontal, VerticalAlignment vertical) {
            CellStyle style = style(workbook, font, fill, horizontal, vertical);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.LIGHT_BLUE.getIndex());
            style.setLeftBorderColor(IndexedColors.LIGHT_BLUE.getIndex());
            style.setRightBorderColor(IndexedColors.LIGHT_BLUE.getIndex());
            style.setTopBorderColor(IndexedColors.LIGHT_BLUE.getIndex());
            return style;
        }
    }

    private Map<LocalDate, String> getVacationStatusByDate(Timesheet timesheet, YearMonth period) {
        Map<LocalDate, String> statuses = new HashMap<>();
        List<VacationRequest> requests = vacationRequestRepository.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                timesheet.getUser().getId(), period.atEndOfMonth(), period.atDay(1));

        for (VacationRequest vacation : requests) {
            vacation.getStartDate().datesUntil(vacation.getEndDate().plusDays(1))
                    .filter(date -> !date.isBefore(period.atDay(1)) && !date.isAfter(period.atEndOfMonth()))
                    .forEach(date -> statuses.put(date, vacation.getVacationType().name() + " - " + vacation.getStatus().name()));
        }

        return statuses;
    }

    private boolean isApprovedVacationStatus(String status) {
        return status != null && (status.endsWith(VacationStatus.APPROVED.name()) || status.endsWith(VacationStatus.LOCKED.name()));
    }

    public List<Map<String, Object>> getMonthlySummary(int year, int month) {
        List<Timesheet> timesheets = timesheetRepository.findByYearAndMonth(year, month);
        return timesheets.stream().filter(this::isEmployeeTimesheet).map(ts -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timesheetId", ts.getId());
            row.put("userId", ts.getUser().getId());
            row.put("employee", ts.getUser().getFullName());
            row.put("status", ts.getStatus().name());
            row.put("totalHours", ts.getTotalHours() != null ? ts.getTotalHours() : BigDecimal.ZERO);
            row.put("approvedHourlyRate", ts.getApprovedHourlyRate());
            row.put("effectiveRate", resolveBillRate(ts, null));
            return row;
        }).sorted((left, right) -> {
            int statusCompare = Integer.compare(statusSortOrder(left.get("status")), statusSortOrder(right.get("status")));
            if (statusCompare != 0) {
                return statusCompare;
            }
            return String.valueOf(left.get("employee")).compareToIgnoreCase(String.valueOf(right.get("employee")));
        }).collect(Collectors.toList());
    }

    private List<Timesheet> filterTimesheets(List<Timesheet> timesheets, List<Long> userIds) {
        var employeeTimesheets = timesheets.stream()
                .filter(this::isEmployeeTimesheet)
                .toList();
        if (userIds == null || userIds.isEmpty()) {
            return employeeTimesheets;
        }
        return employeeTimesheets.stream()
                .filter(timesheet -> userIds.contains(timesheet.getUser().getId()))
                .collect(Collectors.toList());
    }

    private boolean isEmployeeTimesheet(Timesheet timesheet) {
        return timesheet.getUser() != null && !UserRole.SUPER_ADMIN.equals(timesheet.getUser().getRole());
    }

    private int statusSortOrder(Object status) {
        return switch (String.valueOf(status)) {
            case "APPROVED" -> 0;
            case "DRAFT" -> 1;
            case "REJECTED" -> 2;
            default -> 3;
        };
    }
}
