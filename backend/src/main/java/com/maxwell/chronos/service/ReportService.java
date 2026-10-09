package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.TimeEntry;
import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.domain.VacationRequest;
import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.TimeEntryRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Objects;
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
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final CompanyAccessService companyAccess;
    private final JdbcTemplate db;

    public List<Map<String,Object>> approvalPeriods(int year,int month,long requesterId) {
        return approvalPeriods(null,year,month,requesterId);
    }
    public List<Map<String,Object>> approvalPeriods(Long companyId,int year,int month,long requesterId) {
        if(companyId!=null)companyAccess.requireActiveCompanyAccess(companyId,requesterId);
        YearMonth selected=YearMonth.of(year,month);
        return db.queryForList("SELECT a.id,a.user_id,a.project_id,a.period_start,a.period_end,a.frequency,a.status,a.is_late," +
                "p.company_id,p.code AS project_code,p.name AS project_name,m.employee_id,concat_ws(' ',u.first_name,u.last_name) AS user_name," +
                "COALESCE((SELECT sum(e.hours) FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id " +
                "WHERE t.user_id=a.user_id AND e.project_id=a.project_id AND e.entry_date BETWEEN a.period_start AND a.period_end " +
                "AND e.entry_date BETWEEN ? AND ?),0) AS total_hours " +
                "FROM timesheet_approval_periods a JOIN projects p ON p.id=a.project_id JOIN users u ON u.id=a.user_id " +
                "LEFT JOIN company_memberships m ON m.company_id=p.company_id AND m.user_id=a.user_id " +
                "WHERE a.period_start<=? AND a.period_end>=? AND (?::bigint IS NULL OR p.company_id=?) ORDER BY p.code,u.last_name,a.period_start",
                selected.atDay(1),selected.atEndOfMonth(),selected.atEndOfMonth(),selected.atDay(1),companyId,companyId).stream()
                .filter(row->row.get("company_id") instanceof Number company && companyAccess.hasActiveCompanyAccess(company.longValue(),requesterId))
                .filter(row->canReadProjectReport(((Number)row.get("project_id")).longValue(),requesterId))
                .map(row->{
                    var result=new LinkedHashMap<String,Object>();
                    result.put("id",row.get("id")); result.put("projectId",row.get("project_id"));
                    result.put("companyId",row.get("company_id"));
                    result.put("projectCode",row.get("project_code")); result.put("projectName",row.get("project_name"));
                    result.put("userId",row.get("user_id")); result.put("userName",row.get("user_name"));
                    result.put("employeeId",row.get("employee_id")); result.put("periodStart",row.get("period_start"));
                    result.put("periodEnd",row.get("period_end")); result.put("frequency",row.get("frequency"));
                    result.put("status",row.get("status")); result.put("late",row.get("is_late"));
                    result.put("totalHours",row.get("total_hours"));
                    return (Map<String,Object>)result;
                }).toList();
    }

    public byte[] exportApprovalPeriods(List<Long> periodIds,long requesterId) {
        if(periodIds==null || periodIds.isEmpty()) throw new IllegalArgumentException("Select approved periods to export");
        try(ByteArrayOutputStream out=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(out)) {
            for(Long id:periodIds.stream().distinct().toList()) {
                zip.putNextEntry(new ZipEntry("timesheet-period-"+id+".pdf"));
                zip.write(exportApprovalPeriodPdf(id,requesterId));
                zip.closeEntry();
            }
            zip.finish(); return out.toByteArray();
        } catch(IOException ex) { throw new UncheckedIOException(ex); }
    }
    public boolean canReadProjectReport(long project,long actor){
        long company=companyAccess.companyId(project);
        return companyAccess.hasActiveCompanyAccess(company,actor)&&(companyAccess.hasCompanyRole(company,actor,"COMPANY_ADMIN")
            ||companyAccess.mayManageProject(project,actor)||companyAccess.hasProjectRole(project,actor,"PROJECT_MANAGER"));
    }

    public byte[] exportApprovalPeriodPdf(long periodId, long requesterId) {
        Map<String,Object> row=db.queryForMap("SELECT * FROM timesheet_approval_periods WHERE id=?",periodId);
        long userId=((Number)row.get("user_id")).longValue();
        long projectId=((Number)row.get("project_id")).longValue();
        companyAccess.requireActiveCompanyAccess(companyAccess.companyId(projectId),requesterId);
        if(requesterId!=userId && !canReadProjectReport(projectId,requesterId))
            throw new org.springframework.security.access.AccessDeniedException("Timesheet access denied");
        Object correction=row.get("correction_until");
        java.time.Instant correctionUntil=correction instanceof java.sql.Timestamp stamp ? stamp.toInstant()
                : correction instanceof java.time.OffsetDateTime offset ? offset.toInstant() : null;
        if(!"APPROVED".equals(row.get("status")) || correctionUntil!=null && correctionUntil.isAfter(java.time.Instant.now()))
            throw new IllegalArgumentException("Approval period must be finalized before PDF export");
        LocalDate start=row.get("period_start") instanceof LocalDate d ? d : ((java.sql.Date)row.get("period_start")).toLocalDate();
        LocalDate end=row.get("period_end") instanceof LocalDate d ? d : ((java.sql.Date)row.get("period_end")).toLocalDate();
        var employee=userRepository.findById(userId).orElseThrow();
        var project=projectRepository.findById(projectId).orElseThrow();
        var entries=timeEntryRepository.findPeriodEntries(userId,projectId,start,end);
        try(TimesheetPdf pdf=new TimesheetPdf("PERIOD-"+periodId,start+" to "+end,companyAccess.companyDisplayName(project.getCompanyId()))) {
            pdf.section("Employee & reporting period");
            pdf.field("Company",companyAccess.companyDisplayName(project.getCompanyId()));
            pdf.field("Employee",employee.getFullName());
            pdf.field("Employee ID",companyAccess.employmentEmployeeId(project.getCompanyId(),employee.getId()));
            pdf.field("Designation",companyAccess.employmentJobTitle(project.getCompanyId(),employee.getId()));
            pdf.field("Project",project.getCode()+" - "+project.getName());
            pdf.field("Status","APPROVED");
            pdf.section("Approval record");
            Long reviewerId=row.get("reviewed_by_id")==null?null:((Number)row.get("reviewed_by_id")).longValue();
            pdf.field("Approver",reviewerId==null?null:userRepository.findById(reviewerId).map(com.maxwell.chronos.domain.User::getFullName).orElse(null));
            pdf.field("Approval date",row.get("reviewed_at")==null?null:row.get("reviewed_at").toString());
            pdf.field("Approved bill rate",rateLabel((BigDecimal)row.get("approved_bill_rate")));
            pdf.beginEntries();
            if(entries.isEmpty()) pdf.entry("","","","No time entries recorded.");
            else for(TimeEntry entry:entries) writePdfEntry(pdf,entry);
            pdf.total(entries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO,BigDecimal::add)
                    .setScale(2,RoundingMode.HALF_UP).toPlainString());
            return pdf.finish();
        } catch(IOException ex) { throw new UncheckedIOException(ex); }
    }

    public byte[] exportTimesheets(int year, int month, List<Long> userIds) {
        List<Timesheet> timesheets = filterTimesheets(timesheetRepository.findByYearAndMonth(year, month), userIds)
                .stream()
                .filter(this::isReportExportEligible)
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> entryNames = new HashSet<>();
            for (Timesheet ts : timesheets) {
                zip.putNextEntry(new ZipEntry(buildUniqueTimesheetFilename(ts, entryNames)));
                zip.write(buildTimesheetPdf(ts));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] exportProjectTimesheets(List<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one approved project timesheet");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> entryNames = new HashSet<>();
            for (Long submissionId : submissionIds) {
                TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                        .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
                if (!submission.isPdfExportEligible()) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(buildUniqueProjectTimesheetFilename(submission, entryNames)));
                zip.write(buildTimesheetPdf(submission.getTimesheet(), submission));
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
        companyAccess.requireActiveCompanyAccess(timesheet.getCompanyId(),requestingUserId);

        if (!timesheet.getUser().getId().equals(requestingUserId)&&!companyAccess.hasCompanyRole(timesheet.getCompanyId(),requestingUserId,"COMPANY_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to export this timesheet");
        }

        return buildTimesheetWorkbook(timesheet);
    }

    public byte[] exportTimesheetPdfById(Long timesheetId, Long requestingUserId, boolean isAdmin) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        companyAccess.requireActiveCompanyAccess(timesheet.getCompanyId(),requestingUserId);

        if (!timesheet.getUser().getId().equals(requestingUserId)&&!companyAccess.hasCompanyRole(timesheet.getCompanyId(),requestingUserId,"COMPANY_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to export this timesheet");
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
        companyAccess.requireActiveCompanyAccess(timesheet.getCompanyId(),requestingUserId);

        if (!timesheet.getUser().getId().equals(requestingUserId)&&!canReadProjectReport(submission.getProject().getId(),requestingUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to export this timesheet");
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
        String period = displayMonth(Month.of(timesheet.getMonth())) + " " + timesheet.getYear();
        var approvals = submission != null ? List.of(submission)
                : projectSubmissionRepository.findByTimesheetId(timesheet.getId()).stream()
                    .filter(TimesheetProjectSubmission::isPdfExportEligible)
                    .sorted(Comparator.comparing(s -> s.getProject().getCode())).toList();
        String reference = "TS-" + timesheet.getId() + (submission == null ? "" : " / PRJ-" + submission.getId());
        try (TimesheetPdf pdf = new TimesheetPdf(reference, period,companyAccess.companyDisplayName(timesheet.getCompanyId()))) {
            pdf.section("Employee & reporting period");
            pdf.field("Company",companyAccess.companyDisplayName(timesheet.getCompanyId()));
            pdf.field("Employee", timesheet.getUser().getFullName());
            pdf.field("Employee ID", companyAccess.employmentEmployeeId(timesheet.getCompanyId(),timesheet.getUser().getId()));
            pdf.field("Designation", companyAccess.employmentJobTitle(timesheet.getCompanyId(),timesheet.getUser().getId()));
            pdf.field("Project", submission != null
                    ? submission.getProject().getCode() + " - " + submission.getProject().getName()
                    : "All projects for this reporting period");
            pdf.field("Status", (submission != null ? submission.getStatus() : timesheet.getStatus()).name());
            pdf.section("Approval record");
            if (approvals.isEmpty()) {
                pdf.field("Approver", timesheet.getApprovedBy() == null ? null : timesheet.getApprovedBy().getFullName());
                pdf.field("Approval date", timesheet.getApprovedAt() == null ? null : timesheet.getApprovedAt().toLocalDate().toString());
                pdf.field("Approved bill rate", rateLabel(timesheet.getApprovedHourlyRate()));
            } else {
                for (var approval : approvals) {
                    pdf.field("Approved project", approval.getProject().getCode() + " - " + approval.getProject().getName());
                    pdf.field("Approver", approval.getApprovedBy() == null ? null : approval.getApprovedBy().getFullName());
                    pdf.field("Approval date", approval.getApprovedAt() == null ? null : approval.getApprovedAt().toLocalDate().toString());
                    pdf.field("Approved bill rate", rateLabel(projectReportRate(timesheet, approval)));
                }
            }
            List<TimeEntry> entries = timesheet.getTimeEntries().stream()
                    .filter(entry -> includesEntryInPdf(entry, submission, approvals))
                    .sorted(Comparator.comparing(TimeEntry::getEntryDate)
                            .thenComparing(e -> e.getProject() == null ? "" : e.getProject().getCode())
                            .thenComparing(TimeEntry::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (submission == null) {
                List<ProjectSummaryRow> summaryRows = projectSummaryRows(timesheet, approvals);
                if (!summaryRows.isEmpty()) {
                    pdf.beginProjectSummary();
                    BigDecimal totalAmount = BigDecimal.ZERO;
                    boolean hasUnknownAmount = false;
                    for (ProjectSummaryRow row : summaryRows) {
                        pdf.projectSummary(row.project(), hoursLabel(row.hours()), rateSummaryLabel(row.rate()), amountLabel(row.amount()));
                        if (row.amount() == null) {
                            hasUnknownAmount = true;
                        } else {
                            totalAmount = totalAmount.add(row.amount());
                        }
                    }
                    pdf.amountTotal(hasUnknownAmount ? "Unavailable - missing historical rate" : amountLabel(totalAmount));
                }
            }
            if (entries.isEmpty()) {
                pdf.beginEntries();
                pdf.entry("", "", "", "No time entries recorded.");
            } else if (submission == null) {
                Map<String, List<TimeEntry>> entriesByProject = entries.stream()
                        .collect(Collectors.groupingBy(this::projectEntryKey, LinkedHashMap::new, Collectors.toList()));
                for (var group : entriesByProject.entrySet()) {
                    pdf.beginProjectEntries(group.getKey());
                    for (TimeEntry entry : group.getValue()) {
                        writePdfEntry(pdf, entry);
                    }
                }
            } else {
                pdf.beginEntries();
                for (TimeEntry entry : entries) {
                    writePdfEntry(pdf, entry);
                }
            }
            BigDecimal hours = submission != null ? submission.getTotalHours() : timesheet.getTotalHours();
            if (hours == null) hours = entries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            pdf.total(hours.setScale(2, RoundingMode.HALF_UP).toPlainString());
            return pdf.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writePdfEntry(TimesheetPdf pdf, TimeEntry entry) throws IOException {
        String sessions = entry.getSessions().stream()
                .map(session -> session.getLoginTime() + " - " + session.getLogoutTime())
                .collect(Collectors.joining("; "));
        String details = sessions.isBlank() ? "Manual hours" : sessions;
        if (entry.getNotes() != null && !entry.getNotes().isBlank()) details += " | " + entry.getNotes();
        pdf.entry(entry.getEntryDate().toString(), entry.getProject() == null ? "Unassigned" : entry.getProject().getCode(),
                entry.getHours().setScale(2, RoundingMode.HALF_UP).toPlainString(), details);
    }

    private String projectEntryKey(TimeEntry entry) {
        Project project = entry.getProject();
        return project == null ? "Unassigned" : project.getCode() + " - " + project.getName();
    }

    private boolean includesEntryInPdf(TimeEntry entry, TimesheetProjectSubmission submission, List<TimesheetProjectSubmission> approvals) {
        if (submission != null) {
            return entry.getProject() != null && entry.getProject().getId().equals(submission.getProject().getId());
        }
        if (!approvals.isEmpty()) {
            return entry.getProject() != null && approvals.stream()
                    .anyMatch(approval -> approval.getProject().getId().equals(entry.getProject().getId()));
        }
        return true;
    }

    private BigDecimal historicalProjectRate(Timesheet timesheet, TimesheetProjectSubmission submission) {
        if (submission.getApprovedBillRate() != null) return submission.getApprovedBillRate();
        if (timesheet.getPrimaryProject() != null
                && timesheet.getPrimaryProject().getId().equals(submission.getProject().getId())) {
            return timesheet.getApprovedHourlyRate();
        }
        return null;
    }

    private BigDecimal projectReportRate(Timesheet timesheet, TimesheetProjectSubmission submission) {
        BigDecimal historicalRate = historicalProjectRate(timesheet, submission);
        if (historicalRate != null) return historicalRate;
        return projectAssignmentRepository.findByProjectIdAndUserId(submission.getProject().getId(), timesheet.getUser().getId())
                .map(assignment -> assignment.getBillRate() == null ? BigDecimal.ZERO : assignment.getBillRate())
                .orElse(null);
    }

    private String rateLabel(BigDecimal rate) {
        return rate == null ? "Unavailable - no historical rate was recorded"
                : "$" + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + " per hour";
    }

    private String rateSummaryLabel(BigDecimal rate) {
        return rate == null ? "Unavailable"
                : "$" + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "/hr";
    }

    private String amountLabel(BigDecimal amount) {
        return amount == null ? "Unavailable"
                : "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String hoursLabel(BigDecimal hours) {
        return (hours == null ? BigDecimal.ZERO : hours).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<ProjectSummaryRow> projectSummaryRows(Timesheet timesheet, List<TimesheetProjectSubmission> approvals) {
        if (!approvals.isEmpty()) {
            return approvals.stream()
                    .map(approval -> {
                        BigDecimal hours = approval.getTotalHours();
                        if (hours == null) {
                            hours = timesheet.getTimeEntries().stream()
                                    .filter(entry -> entry.getProject() != null
                                            && entry.getProject().getId().equals(approval.getProject().getId()))
                                    .map(TimeEntry::getHours)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                        }
                        BigDecimal rate = projectReportRate(timesheet, approval);
                        return new ProjectSummaryRow(
                                approval.getProject().getCode() + " - " + approval.getProject().getName(),
                                hours,
                                rate,
                                rate == null ? null : hours.multiply(rate));
                    })
                    .toList();
        }

        BigDecimal hours = timesheet.getTotalHours();
        if (hours == null) {
            hours = timesheet.getTimeEntries().stream()
                    .map(TimeEntry::getHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal rate = timesheet.getApprovedHourlyRate();
        return List.of(new ProjectSummaryRow(
                timesheet.getPrimaryProject() == null ? "All projects" : timesheet.getPrimaryProject().getCode() + " - " + timesheet.getPrimaryProject().getName(),
                hours,
                rate,
                rate == null ? null : hours.multiply(rate)));
    }

    private BigDecimal resolveBillRate(Timesheet timesheet, TimesheetProjectSubmission submission) {
        return resolveBillRate(timesheet, submission, false);
    }

    private BigDecimal resolveBillRate(Timesheet timesheet, TimesheetProjectSubmission submission, boolean allowMissingHistoricalRate) {
        if (submission != null && submission.isPdfExportEligible()) {
            if (submission.getApprovedBillRate() == null) {
                if (allowMissingHistoricalRate) return null;
                throw new IllegalArgumentException("Historical approved rate is unavailable; reopen and reapprove this project timesheet");
            }
            return submission.getApprovedBillRate();
        }
        if (submission == null) {
            var submissions = projectSubmissionRepository.findByTimesheetId(timesheet.getId()).stream()
                    .filter(TimesheetProjectSubmission::isPdfExportEligible)
                    .filter(s -> s.getTotalHours() != null && s.getTotalHours().signum() > 0).toList();
            if (!submissions.isEmpty()) {
                BigDecimal hours = BigDecimal.ZERO, amount = BigDecimal.ZERO;
                for (var project : submissions) {
                    BigDecimal rate = resolveBillRate(timesheet, project, allowMissingHistoricalRate);
                    if (rate == null) return null;
                    hours = hours.add(project.getTotalHours());
                    amount = amount.add(project.getTotalHours().multiply(rate));
                }
                return amount.divide(hours, 6, RoundingMode.HALF_UP);
            }
            if (timesheet.getStatus() == TimesheetStatus.APPROVED || timesheet.isLocked()) {
                if (timesheet.getApprovedHourlyRate() == null && !allowMissingHistoricalRate) throw new IllegalArgumentException("Historical approved rate is unavailable");
                return timesheet.getApprovedHourlyRate();
            }
        }
        if (submission != null) {
            return projectAssignmentRepository.findByProjectIdAndUserId(submission.getProject().getId(), timesheet.getUser().getId())
                    .map(a -> a.getBillRate() == null ? BigDecimal.ZERO : a.getBillRate()).orElse(BigDecimal.ZERO);
        }
        return timesheet.getBillRate() == null ? BigDecimal.ZERO : timesheet.getBillRate();
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

            BigDecimal rate = resolveBillRate(timesheet, null, true);
            YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
            Map<LocalDate, BigDecimal> hoursByDate = timesheet.getTimeEntries().stream()
                    .collect(Collectors.toMap(TimeEntry::getEntryDate, TimeEntry::getHours, BigDecimal::add));
            Map<LocalDate, String> vacationStatusByDate = getVacationStatusByDate(timesheet, period);
            CellStyle vacationDateStyle = cloneWithFill(workbook, cellAt(sheet, 13, 1).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationValueStyle = cloneWithFill(workbook, cellAt(sheet, 13, 2).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationHoursStyle = cloneWithFill(workbook, cellAt(sheet, 13, 6).getCellStyle(), IndexedColors.LIGHT_GREEN);
            CellStyle vacationNoteStyle = cloneWithFill(workbook, cellAt(sheet, 25, 9).getCellStyle(), IndexedColors.LIGHT_GREEN);

            setCellValue(sheet, 1, 1, "TIMESHEET for " + displayMonth(period.getMonth()) + " (" + period.getYear() + ")");
            setCellValue(sheet, 1, 9, rate == null ? "Pay rate: Unavailable - no historical rate was recorded"
                    : "Pay rate: $" + rate.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " per hour");
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

                String vacationStatus = date != null ? vacationStatusByDate.getOrDefault(date, "") : "";
                boolean approvedVacation = isApprovedVacationStatus(vacationStatus);
                BigDecimal hours = hoursByDate.getOrDefault(date, BigDecimal.ZERO);
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

                String note = timesheet.getTimeEntries().stream().filter(e -> java.util.Objects.equals(date, e.getEntryDate()))
                        .map(TimeEntry::getNotes).filter(java.util.Objects::nonNull).filter(n -> !n.isBlank())
                        .collect(Collectors.joining("; "));
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
        return safeName + "," + displayMonth(period.getMonth()) + "," + period.getYear() + ".pdf";
    }

    private String buildUniqueTimesheetFilename(Timesheet timesheet, Set<String> entryNames) {
        String filename = buildTimesheetFilename(timesheet);
        if (entryNames.add(filename)) {
            return filename;
        }

        String baseName = filename.substring(0, filename.length() - ".pdf".length());
        String employeeId=companyAccess.employmentEmployeeId(timesheet.getCompanyId(),timesheet.getUser().getId());
        String suffix = employeeId != null && !employeeId.isBlank()
                ? employeeId
                : "timesheet_" + timesheet.getId();
        String candidate = baseName + "_" + suffix.replaceAll("[^A-Za-z0-9]+", "_") + ".pdf";

        int counter = 2;
        while (!entryNames.add(candidate)) {
            candidate = baseName + "_" + suffix.replaceAll("[^A-Za-z0-9]+", "_") + "_" + counter + ".pdf";
            counter++;
        }

        return candidate;
    }

    private String buildProjectTimesheetFilename(TimesheetProjectSubmission submission) {
        Timesheet timesheet = submission.getTimesheet();
        YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        String safeEmployee = timesheet.getUser().getFullName().trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        String safeProject = submission.getProject().getCode().trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safeEmployee.isBlank()) safeEmployee = "employee_" + timesheet.getUser().getId();
        if (safeProject.isBlank()) safeProject = "project_" + submission.getProject().getId();
        return safeProject + "_" + safeEmployee + "," + displayMonth(period.getMonth()) + "," + period.getYear() + ".pdf";
    }

    private String buildUniqueProjectTimesheetFilename(TimesheetProjectSubmission submission, Set<String> entryNames) {
        String filename = buildProjectTimesheetFilename(submission);
        if (entryNames.add(filename)) {
            return filename;
        }

        String baseName = filename.substring(0, filename.length() - ".pdf".length());
        String candidate = baseName + "_submission_" + submission.getId() + ".pdf";
        int counter = 2;
        while (!entryNames.add(candidate)) {
            candidate = baseName + "_submission_" + submission.getId() + "_" + counter + ".pdf";
            counter++;
        }
        return candidate;
    }

    private record ProjectSummaryRow(String project, BigDecimal hours, BigDecimal rate, BigDecimal amount) {}

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
            if(!Objects.equals(vacation.getCompanyId(),timesheet.getCompanyId()))continue;
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
            row.put("effectiveRate", resolveBillRate(ts, null, true));
            row.put("exportEligible", isReportExportEligible(ts));
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
        return timesheet.getUser() != null && !UserRole.ADMIN.equals(timesheet.getUser().getRole());
    }

    private boolean isReportExportEligible(Timesheet timesheet) {
        List<TimesheetProjectSubmission> submissions = projectSubmissionRepository.findByTimesheetId(timesheet.getId());
        if (!submissions.isEmpty()) {
            return submissions.stream().anyMatch(TimesheetProjectSubmission::isPdfExportEligible);
        }
        return TimesheetStatus.APPROVED.equals(timesheet.getStatus()) || timesheet.isLocked();
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
