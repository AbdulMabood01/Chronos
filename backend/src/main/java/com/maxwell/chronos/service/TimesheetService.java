package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.TimeEntry;
import com.maxwell.chronos.domain.TimeEntrySession;
import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.domain.VacationRequest;
import com.maxwell.chronos.dto.TimeEntryDTO;
import com.maxwell.chronos.dto.TimeEntrySessionDTO;
import com.maxwell.chronos.dto.TimesheetDTO;
import com.maxwell.chronos.dto.TimesheetProjectSubmissionDTO;
import com.maxwell.chronos.dto.VacationDayDTO;
import com.maxwell.chronos.enums.ProjectStatus;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.ProjectHourPlanRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.TimeEntryRepository;
import com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.VacationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TimesheetService {
    private final TimesheetPeriodService approvalPeriods;
    public static final int EDIT_DAYS_AFTER_MONTH_END = 7;
    public static final int APPROVED_OPENING_DAYS = 7;
    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimesheetProjectSubmissionRepository projectSubmissionRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectHourPlanRepository projectHourPlanRepository;
    private final VacationRequestRepository vacationRequestRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ProjectService projectService;
    private final CompanyAccessService access;

    public List<com.maxwell.chronos.dto.AuditLogDTO> getApprovalHistory(Long timesheetId, Long projectId, User requester) {
        Timesheet sheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (requester == null || !canViewProjectSubmission(sheet, project, requester))
            throw new org.springframework.security.access.AccessDeniedException("Timesheet history permission required");
        var history = new java.util.ArrayList<com.maxwell.chronos.dto.AuditLogDTO>();
        projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheetId, projectId).ifPresent(submission ->
                history.addAll(auditService.getAuditLogsByEntityTypeAndId("TimesheetProjectSubmission", submission.getId())));
        history.addAll(auditService.getAuditLogsByEntityTypeAndId("Timesheet", timesheetId).stream()
                .filter(event -> event.getAction() == com.maxwell.chronos.enums.AuditAction.TIMESHEET_REOPENED).toList());
        var actions = java.util.Set.of(com.maxwell.chronos.enums.AuditAction.TIMESHEET_SUBMITTED,
                com.maxwell.chronos.enums.AuditAction.TIMESHEET_APPROVED, com.maxwell.chronos.enums.AuditAction.TIMESHEET_REJECTED,
                com.maxwell.chronos.enums.AuditAction.TIMESHEET_REOPENED,
                com.maxwell.chronos.enums.AuditAction.PROJECT_MANAGER_CHANGED);
        return history.stream().filter(event -> actions.contains(event.getAction()))
                .sorted(Comparator.comparing(com.maxwell.chronos.dto.AuditLogDTO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(com.maxwell.chronos.dto.AuditLogDTO::getId, Comparator.reverseOrder()))
                .toList();
    }

    public record MissingTimesheet(Long userId, String userName, Long projectId, String projectCode,
                                   Long timesheetId, String status, BigDecimal hours,
                                   LocalDate periodStart, LocalDate periodEnd, boolean late) {
        public MissingTimesheet(Long userId,String userName,Long projectId,String projectCode,Long timesheetId,String status,BigDecimal hours) {
            this(userId,userName,projectId,projectCode,timesheetId,status,hours,null,null,false);
        }
    }

    public List<MissingTimesheet> getMissingTimesheets(int year, int month, User reviewer) {
        YearMonth period = YearMonth.of(year, month);
        if (reviewer == null||access.hasPlatformRole(reviewer.getId(),"PLATFORM_ADMIN")||!(access.hasAnyCompanyRole(reviewer.getId(),"COMPANY_ADMIN")||projectService.canManageProjects(reviewer.getId())||projectService.canReviewProjects(reviewer.getId())))
            throw new org.springframework.security.access.AccessDeniedException("Manager permission required");
        var result = new java.util.ArrayList<MissingTimesheet>();
        var visibleProjects = projectService.getProjects(reviewer).stream().map(project->project.getId()).collect(Collectors.toSet());
        for (var assignment : projectAssignmentRepository.findAll()) {
            var employee = assignment.getUser();
            var project = assignment.getProject();
            if (!visibleProjects.contains(project.getId()) || !Boolean.TRUE.equals(project.getIsActive())
                    || project.getStatus() != com.maxwell.chronos.enums.ProjectStatus.ACTIVE) continue;
            if (!access.hasCompanyRole(project.getCompanyId(),reviewer.getId(),"COMPANY_ADMIN")&&!access.hasProjectRole(project.getId(),reviewer.getId(),"PROJECT_MANAGER")&&!access.mayManageProject(project.getId(),reviewer.getId())
                    && !access.mayReview(project.getId(),reviewer.getId(),employee.getId(),false,"Queue preview")) continue;
            if (!access.maySubmit(project.getId(), employee.getId()) || !Boolean.TRUE.equals(employee.getIsActive())
                    || (assignment.getStartDate() != null && assignment.getStartDate().isAfter(period.atEndOfMonth()))
                    || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(period.atDay(1)))
                    || (!Boolean.TRUE.equals(assignment.getIsActive()) && assignment.getEndDate() == null)) continue;
            var seen = new java.util.HashSet<LocalDate>();
            LocalDate employeeToday=LocalDate.now(java.time.ZoneId.of(employee.getTimezone()));
            for (LocalDate day=period.atDay(1);!day.isAfter(period.atEndOfMonth());day=day.plusDays(1)) {
                var approvalPeriod=approvalPeriods.period(project.getId(),day);
                if (!seen.add(approvalPeriod.start())) continue;
                if (approvalPeriod.start().isAfter(employeeToday)) continue;
                var snapshot=approvalPeriods.view(employee.getId(),employee.getId(),project.getId(),day);
                if (!Boolean.TRUE.equals(snapshot.get("requiresSubmission"))) continue;
                String status=(String)snapshot.get("status");
                BigDecimal hours=(BigDecimal)snapshot.get("totalHours");
                boolean late=Boolean.TRUE.equals(snapshot.get("late"));
                result.add(new MissingTimesheet(employee.getId(),employee.getFullName(),project.getId(),project.getCode(),
                        (Long)snapshot.get("timesheetId"),status.equals("DRAFT") && !late ? "IN_PROGRESS"
                                : status.equals("DRAFT") && hours.signum()==0 ? "NOT_STARTED" : status,hours,
                        approvalPeriod.start(),approvalPeriod.end(),late));
            }
        }
        result.sort(Comparator.comparing(MissingTimesheet::userName).thenComparing(MissingTimesheet::projectCode));
        return result;
    }

    public TimesheetDTO getOrCreateTimesheet(Long userId, int year, int month) {
        Long companyId = access.companyIds(userId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No active company membership"));
        return getOrCreateTimesheet(userId, companyId, year, month);
    }

    public TimesheetDTO getOrCreateTimesheet(Long userId, Long companyId, int year, int month) {
        YearMonth.of(year, month);
        if (companyId == null || !access.companyIds(userId).contains(companyId))
            throw new org.springframework.security.access.AccessDeniedException("Company membership required");
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Timesheet timesheet = timesheetRepository.findPeriodForUpdate(userId, companyId, year, month)
                .orElseGet(() -> {
                    Timesheet newTimesheet = Timesheet.builder()
                            .user(user)
                            .companyId(companyId)
                            .year(year)
                            .month(month)
                            .status(TimesheetStatus.DRAFT)
                            .billRate(BigDecimal.ZERO)
                            .billRateUpdatedAt(java.time.LocalDateTime.now())
                            .build();
                    Timesheet saved = timesheetRepository.save(newTimesheet);
                    auditService.logAction(userId, "TIMESHEET_CREATED", "Timesheet", saved.getId(),
                            "Year: " + year + ", Month: " + month);
                    return saved;
                });

        return toDTO(timesheet);
    }

    public TimesheetDTO getTimesheetById(Long timesheetId, Long requestingUserId, boolean isAdmin) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        access.requireActiveCompanyAccess(timesheet.getCompanyId(),requestingUserId);

        if (!isAdmin && !timesheet.getUser().getId().equals(requestingUserId)
                && !canReviewTimesheet(timesheet, requestingUserId)) {
            throw new IllegalArgumentException("User cannot view another user's timesheet");
        }

        return toDTO(timesheet);
    }

    public TimesheetDTO getProjectTimesheet(Long timesheetId, Long projectId, User requester) {
        var submission = getProjectSubmission(timesheetId, projectId, requester);
        var sheet = timesheetRepository.findForUpdate(timesheetId).orElseThrow();
        if (sheet.getUser().getId().equals(requester.getId())) return toDTO(sheet);
        var entries = sheet.getTimeEntries().stream()
                .filter(entry -> entry.getProject() != null && projectId.equals(entry.getProject().getId()))
                .map(this::toTimeEntryDTO).collect(Collectors.toSet());
        return TimesheetDTO.builder()
                .id(sheet.getId()).userId(sheet.getUser().getId()).companyId(sheet.getCompanyId()).userName(sheet.getUser().getFullName())
                .userJobTitle(access.employmentJobTitle(sheet.getCompanyId(),sheet.getUser().getId())).year(sheet.getYear()).month(sheet.getMonth())
                .primaryProjectId(projectId).primaryProjectCode(submission.getProjectCode()).primaryProjectName(submission.getProjectName())
                .status(submission.getStatus()).totalHours(entries.stream().map(TimeEntryDTO::getHours).reduce(BigDecimal.ZERO, BigDecimal::add))
                .editable(false).pdfExportEligible(submission.getPdfExportEligible())
                .timeEntries(entries).vacationDays(Set.of()).build();
    }

    public TimesheetDTO submitTimesheet(Long timesheetId, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (!timesheet.getUser().getId().equals(userId)) throw new IllegalArgumentException("Not your timesheet");
        requireCanSubmit(timesheet.getUser());
        Set<Long> projects = getEntryProjectIds(timesheet);
        if (projects.isEmpty() || timesheet.getTimeEntries().stream().anyMatch(e -> e.getProject() == null)) {
            throw new IllegalArgumentException("Every entry must have a project");
        }
        boolean submitted = false;
        for (Long projectId : projects) {
            var existing = projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheetId, projectId);
            if (existing.isEmpty() || existing.get().isEditable()) {
                submitProjectTimesheet(timesheetId, projectId, userId);
                submitted = true;
            }
        }
        if (!submitted) throw new IllegalArgumentException("No editable project timesheets to submit");
        return toDTO(timesheet);
    }

    public TimesheetDTO approveTimesheet(Long timesheetId, Long approvingUserId) {
        return reviewMonthlyProjects(timesheetId, approvingUserId, null, true);
    }

    public TimesheetDTO rejectTimesheet(Long timesheetId, String reason, Long rejectingUserId) {
        return reviewMonthlyProjects(timesheetId, rejectingUserId, reason, false);
    }

    private TimesheetDTO reviewMonthlyProjects(Long timesheetId, Long reviewerId, String reason, boolean approve) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        var pending = projectSubmissionRepository.findByTimesheetId(timesheetId).stream()
                .filter(submission -> submission.getStatus() == TimesheetStatus.SUBMITTED
                        || submission.getStatus() == TimesheetStatus.CHANGE_REQUESTED).toList();
        if (pending.isEmpty()) throw new IllegalArgumentException("No submitted project timesheets to review");
        for (var submission : pending) {
            if (approve) approveProjectSubmission(submission.getId(), reviewerId);
            else rejectProjectSubmission(submission.getId(), reason, reviewerId);
        }
        return toDTO(timesheet);
    }

    public TimesheetProjectSubmissionDTO openAfterApprovedRequest(Long timesheetId, Long projectId, String reason, Long projectAdminId) {
        User projectAdmin = userRepository.findById(projectAdminId)
                .orElseThrow(() -> new IllegalArgumentException("Project Admin not found"));
        if (!access.mayManageProject(projectId, projectAdminId))
            throw new org.springframework.security.access.AccessDeniedException("Only Project Admin can approve opening requests");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new IllegalArgumentException("A reason of up to 500 characters is required");
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (YearMonth.of(timesheet.getYear(), timesheet.getMonth()).isAfter(YearMonth.now()))
            throw new IllegalArgumentException("Future timesheets cannot be opened");
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!project.getCompanyId().equals(timesheet.getCompanyId()))
            throw new IllegalArgumentException("Project belongs to a different company calendar");
        var assignment = projectAssignmentRepository.findByProjectIdAndUserId(projectId, timesheet.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Project assignment not found"));
        YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        if ((assignment.getStartDate() != null && assignment.getStartDate().isAfter(period.atEndOfMonth()))
                || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(period.atDay(1))))
            throw new IllegalArgumentException("Project assignment did not cover this timesheet month");
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, project);
        if (submission.getStatus() == TimesheetStatus.SUBMITTED || submission.getStatus() == TimesheetStatus.CHANGE_REQUESTED)
            throw new IllegalArgumentException("A submitted project timesheet must be reviewed before reopening");
        if (submission.getCorrectionPlannedHours() == null)
            submission.setCorrectionPlannedHours(resolvePlannedHours(timesheet, project)
                    .max(submission.getTotalHours() == null ? BigDecimal.ZERO : submission.getTotalHours()));
        if (!submission.isEditable() || timesheet.isLocked() || timesheet.getStatus() == TimesheetStatus.APPROVED
                || timesheet.isApprovalFrozen()) {
            submission.setStatus(TimesheetStatus.DRAFT);
            submission.setApprovedAt(null);
            submission.setApprovedBy(null);
            submission.setApprovedBillRate(null);
            submission.setSubmittedAt(null);
            submission.setRejectedAt(null);
            submission.setRejectedBy(null);
            submission.setRejectionReason(null);
            timesheet.setStatus(TimesheetStatus.DRAFT);
            timesheet.setApprovedAt(null);
            timesheet.setApprovedBy(null);
            timesheet.setApprovedHourlyRate(null);
            timesheetRepository.save(timesheet);
        }
        submission.setCorrectionUntil(java.time.LocalDateTime.now().plusDays(APPROVED_OPENING_DAYS));
        projectSubmissionRepository.save(submission);
        auditService.logAction(projectAdminId, "TIMESHEET_REOPENED", "TimesheetProjectSubmission", submission.getId(),
                "Opening request approved: " + reason.trim());
        notificationService.createNotification(timesheet.getUser().getId(), "TIMESHEET_REOPENED", "Correction window opened",
                "You can enter or correct " + submission.getProject().getCode() + " hours until " + submission.getCorrectionUntil(),
                submission.getId(), "TimesheetProjectSubmission");
        return toProjectSubmissionDTO(submission);
    }

    public TimeEntryDTO addTimeEntry(Long timesheetId, LocalDate entryDate, BigDecimal hours, String notes,
                                     Long projectId, List<TimeEntrySessionDTO> sessions, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot edit another user's timesheet");
        }

        var project = requireAssignedProject(projectId, userId, timesheet);
        approvalPeriods.requireEditable(userId, projectId, entryDate);
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, project);
        if (java.time.YearMonth.of(timesheet.getYear(), timesheet.getMonth()).isAfter(java.time.YearMonth.now())) {
            throw new IllegalArgumentException("Future timesheets are read-only until that month begins");
        }

        if (isApprovedVacationDay(timesheet, entryDate) && hours.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Approved vacation days cannot have billable hours");
        }

        // Validate hours
        if (hours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hours cannot be negative");
        }
        if (hours.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Hours cannot exceed 24 per day");
        }

        TimeEntry entry = timeEntryRepository.findByTimesheetIdAndEntryDateAndProjectId(timesheetId, entryDate, projectId)
                .orElseGet(() -> TimeEntry.builder()
                        .timesheet(timesheet)
                        .entryDate(entryDate)
                        .build());
        validateEntry(timesheet, project, entry.getEntryDate(), entry.getId(), hours, sessions);
        ensureWithinProjectHourPlan(timesheet, project, entry.getId(), hours);
        entry.setHours(hours);
        entry.setNotes(notes);
        entry.setProject(project);
        applySessions(entry, sessions);

        TimeEntry saved = timeEntryRepository.save(entry);
        timesheet.getTimeEntries().add(saved);
        timesheet.calculateTotalHours();
        timesheetRepository.save(timesheet);
        syncProjectSubmissionTotals(submission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_EDITED", "Timesheet", timesheetId,
                "Added entry for " + entryDate + " with " + hours + " hours");
        notifyTimesheetUpdated(userId, timesheetId);

        return toTimeEntryDTO(saved);
    }

    public TimeEntryDTO updateTimeEntry(Long timesheetId, Long entryId, BigDecimal hours, String notes,
                                        Long projectId, List<TimeEntrySessionDTO> sessions, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot edit another user's timesheet");
        }

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Time entry not found"));

        if (!entry.getTimesheet().getId().equals(timesheetId)) {
            throw new IllegalArgumentException("Time entry does not belong to this timesheet");
        }

        if (hours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hours cannot be negative");
        }
        if (hours.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Hours cannot exceed 24 per day");
        }

        if (isApprovedVacationDay(timesheet, entry.getEntryDate()) && hours.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Approved vacation days cannot have billable hours");
        }

        Project project = requireAssignedProject(projectId, userId, timesheet);
        approvalPeriods.requireEditable(userId, projectId, entry.getEntryDate());
        if (entry.getProject() != null && !entry.getProject().getId().equals(projectId))
            approvalPeriods.requireEditable(userId, entry.getProject().getId(), entry.getEntryDate());
        if (entry.getProject() != null) requireAssignedProject(entry.getProject().getId(), userId, timesheet);
        TimesheetProjectSubmission existingSubmission = getOrCreateProjectSubmission(timesheet, entry.getProject() != null ? entry.getProject() : project);
        TimesheetProjectSubmission targetSubmission = getOrCreateProjectSubmission(timesheet, project);
        validateEntry(timesheet, project, entry.getEntryDate(), entry.getId(), hours, sessions);
        ensureWithinProjectHourPlan(timesheet, project, entry.getId(), hours);

        entry.setHours(hours);
        entry.setNotes(notes);
        entry.setProject(project);
        applySessions(entry, sessions);
        TimeEntry saved = timeEntryRepository.save(entry);
        timesheet.calculateTotalHours();
        timesheetRepository.save(timesheet);
        syncProjectSubmissionTotals(existingSubmission);
        syncProjectSubmissionTotals(targetSubmission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_EDITED", "Timesheet", timesheetId,
                "Updated entry " + entryId);
        notifyTimesheetUpdated(userId, timesheetId);

        return toTimeEntryDTO(saved);
    }

    public void deleteTimeEntry(Long timesheetId, Long entryId, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot edit another user's timesheet");
        }

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Time entry not found"));

        if (!entry.getTimesheet().getId().equals(timesheetId)) {
            throw new IllegalArgumentException("Time entry does not belong to this timesheet");
        }

        if (entry.getProject() == null) {
            throw new IllegalArgumentException("Time entry does not have a project code");
        }
        requireAssignedProject(entry.getProject().getId(), userId, timesheet);
        approvalPeriods.requireEditable(userId, entry.getProject().getId(), entry.getEntryDate());
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, entry.getProject());

        timesheet.getTimeEntries().remove(entry);
        timeEntryRepository.delete(entry);
        timesheet.calculateTotalHours();
        timesheetRepository.save(timesheet);
        syncProjectSubmissionTotals(submission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_EDITED", "Timesheet", timesheetId,
                "Deleted entry " + entryId);
        notifyTimesheetUpdated(userId, timesheetId);
    }

    private void notifyTimesheetUpdated(Long userId, Long timesheetId) {
        notificationService.createNotification(userId, "TIMESHEET_UPDATED", "Timesheet updated",
                "Your timesheet hours have been updated.", timesheetId, "Timesheet");
    }

    public List<TimesheetDTO> getTimesheetsByUser(Long userId) {
        return timesheetRepository.findByUserId(userId).stream()
                .filter(sheet->access.hasActiveCompanyAccess(sheet.getCompanyId(),userId))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TimesheetDTO> getPendingTimesheets(User reviewer) {
        if (reviewer == null || !projectService.canReviewProjects(reviewer.getId())) {
            throw new IllegalArgumentException("Reviewer permission required");
        }
        List<Timesheet> submitted = timesheetRepository.findByStatus(TimesheetStatus.SUBMITTED);
        List<Timesheet> changeRequests = timesheetRepository.findByStatus(TimesheetStatus.CHANGE_REQUESTED);
        return java.util.stream.Stream.concat(submitted.stream(), changeRequests.stream())
                .filter(timesheet -> canReviewTimesheet(timesheet, reviewer.getId()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TimesheetProjectSubmissionDTO> getPendingProjectSubmissions(User reviewer) {
        if (reviewer == null || !projectService.canReviewProjects(reviewer.getId())) {
            throw new IllegalArgumentException("Reviewer permission required");
        }
        List<TimesheetProjectSubmission> submitted = projectSubmissionRepository.findByStatus(TimesheetStatus.SUBMITTED);
        List<TimesheetProjectSubmission> changeRequests = projectSubmissionRepository.findByStatus(TimesheetStatus.CHANGE_REQUESTED);
        return java.util.stream.Stream.concat(submitted.stream(), changeRequests.stream())
                .filter(submission -> canReviewProjectSubmission(submission, reviewer))
                .map(submission -> reviewDTO(submission, reviewer))
                .collect(Collectors.toList());
    }

    public TimesheetProjectSubmissionDTO getProjectSubmission(Long timesheetId, Long projectId, User requestingUser) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!project.getCompanyId().equals(timesheet.getCompanyId()))
            throw new IllegalArgumentException("Project belongs to a different company calendar");

        if (!canViewProjectSubmission(timesheet, project, requestingUser)) {
            throw new IllegalArgumentException("User cannot view this project timesheet");
        }

        return reviewDTO(projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheetId, projectId)
                .orElseGet(() -> TimesheetProjectSubmission.builder().timesheet(timesheet).project(project)
                        .status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ZERO).build()), requestingUser);
    }

    public TimesheetProjectSubmissionDTO submitProjectTimesheet(Long timesheetId, Long projectId, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot submit another user's timesheet");
        }
        requireCanSubmit(timesheet.getUser());

        Project project = requireAssignedProject(projectId, userId, timesheet);
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, project);
        requireEditingPeriod(timesheet, submission);
        if (timesheet.isLocked() || !submission.isEditable()) {
            throw new IllegalArgumentException("Project timesheet cannot be submitted from its current status");
        }

        syncProjectSubmissionTotals(submission);
        BigDecimal plannedHours = resolvePlannedHours(timesheet, project);
        if (submission.getTotalHours() != null && submission.getTotalHours().compareTo(plannedHours) > 0) {
            throw new IllegalArgumentException("Project timesheet exceeds planned hours");
        }
        if (submission.getTotalHours() == null || submission.getTotalHours().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Project timesheet cannot be empty");
        }

        submission.setApprovedBillRate(null);
        submission.setAssignedApprover(project.getProjectManager() != null && project.getProjectManager().getId().equals(userId)
                ? project.getProjectManagerHoursApprover() : project.getProjectManager());
        submission.setStatus(TimesheetStatus.SUBMITTED);
        submission.setSubmittedAt(java.time.LocalDateTime.now());
        submission.setApprovedAt(null);
        submission.setApprovedBy(null);
        submission.setRejectedAt(null);
        submission.setRejectedBy(null);
        submission.setRejectionReason(null);
        submission.setCorrectionUntil(null);
        submission.setCorrectionPlannedHours(null);
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_SUBMITTED", "TimesheetProjectSubmission", saved.getId(),
                "Project: " + project.getCode() + ", Year: " + timesheet.getYear() + ", Month: " + timesheet.getMonth());

        notifyReviewersOfProjectSubmission(saved);
        return toProjectSubmissionDTO(saved);
    }

    public TimesheetProjectSubmissionDTO approveProjectSubmission(Long submissionId, Long approvingUserId) {
        return approveProjectSubmission(submissionId, approvingUserId, null);
    }

    public TimesheetProjectSubmissionDTO approveProjectSubmission(Long submissionId, Long approvingUserId, String fallbackReason) {
        User approvingUser = userRepository.findById(approvingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Approving user not found"));
        Long parentId = projectSubmissionRepository.findTimesheetId(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
        timesheetRepository.findForUpdate(parentId).orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));

        requireProjectSubmissionReviewer(submission, approvingUser, fallbackReason);
        if (!TimesheetStatus.SUBMITTED.equals(submission.getStatus())
                && !TimesheetStatus.CHANGE_REQUESTED.equals(submission.getStatus())) {
            throw new IllegalArgumentException("Only submitted project timesheets can be approved");
        }

        syncProjectSubmissionTotals(submission);
        submission.setApprovedBillRate(resolveProjectBillRate(submission.getProject(), submission.getTimesheet().getUser()));
        submission.setStatus(TimesheetStatus.APPROVED);
        submission.setApprovedAt(java.time.LocalDateTime.now());
        submission.setApprovedBy(approvingUser);
        submission.setRejectedAt(null);
        submission.setRejectedBy(null);
        submission.setRejectionReason(null);
        submission.setCorrectionUntil(null);
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(saved.getTimesheet());

        String approvalAudit = "Employee: " + saved.getTimesheet().getUser().getFullName() + ", Project: "
                + saved.getProject().getCode() + (fallbackReason == null ? "" : "; Project Admin fallback: " + fallbackReason.trim());
        if (isProjectAdminFallback(saved, approvingUserId))
            auditService.logRequiredAction(approvingUserId, com.maxwell.chronos.enums.AuditAction.TIMESHEET_APPROVED,
                    "TimesheetProjectSubmission", saved.getId(), approvalAudit);
        else auditService.logAction(approvingUserId, "TIMESHEET_APPROVED", "TimesheetProjectSubmission", saved.getId(), approvalAudit);
        notificationService.createNotification(saved.getTimesheet().getUser().getId(),
                "TIMESHEET_APPROVED",
                "Project Timesheet Approved",
                saved.getProject().getCode() + " for " + saved.getTimesheet().getMonth() + "/" + saved.getTimesheet().getYear() + " has been approved.",
                saved.getId(),
                "TimesheetProjectSubmission");

        return toProjectSubmissionDTO(saved);
    }

    public TimesheetProjectSubmissionDTO rejectProjectSubmission(Long submissionId, String rejectionReason, Long rejectingUserId) {
        return rejectProjectSubmission(submissionId, rejectionReason, rejectingUserId, null);
    }

    public TimesheetProjectSubmissionDTO rejectProjectSubmission(Long submissionId, String rejectionReason, Long rejectingUserId, String fallbackReason) {
        User rejectingUser = userRepository.findById(rejectingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Rejecting user not found"));
        Long parentId = projectSubmissionRepository.findTimesheetId(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
        timesheetRepository.findForUpdate(parentId).orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));

        requireProjectSubmissionReviewer(submission, rejectingUser, fallbackReason);
        if (submission.getTimesheet().getUser().getId().equals(rejectingUserId)) {
            throw new IllegalArgumentException("Users cannot reject their own timesheets");
        }
        if (!TimesheetStatus.SUBMITTED.equals(submission.getStatus())
                && !TimesheetStatus.CHANGE_REQUESTED.equals(submission.getStatus())) {
            throw new IllegalArgumentException("Only submitted project timesheets can be rejected");
        }

        if (rejectionReason == null || rejectionReason.isBlank()) throw new IllegalArgumentException("Rejection reason is required");
        if (rejectionReason.trim().length() > 500) throw new IllegalArgumentException("Rejection reason cannot exceed 500 characters");
        rejectionReason = rejectionReason.trim();
        submission.setApprovedBillRate(null);
        submission.setStatus(TimesheetStatus.REJECTED);
        submission.setRejectedAt(java.time.LocalDateTime.now());
        submission.setRejectedBy(rejectingUser);
        submission.setRejectionReason(rejectionReason);
        submission.setCorrectionPlannedHours(resolvePlannedHours(submission.getTimesheet(), submission.getProject()));
        submission.setCorrectionUntil(java.time.LocalDateTime.now().plusDays(APPROVED_OPENING_DAYS));
        submission.setApprovedAt(null);
        submission.setApprovedBy(null);
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(saved.getTimesheet());

        String rejectionAudit = "Reason: " + rejectionReason
                + (fallbackReason == null ? "" : "; Project Admin fallback: " + fallbackReason.trim());
        if (isProjectAdminFallback(saved, rejectingUserId))
            auditService.logRequiredAction(rejectingUserId, com.maxwell.chronos.enums.AuditAction.TIMESHEET_REJECTED,
                    "TimesheetProjectSubmission", saved.getId(), rejectionAudit);
        else auditService.logAction(rejectingUserId, "TIMESHEET_REJECTED", "TimesheetProjectSubmission", saved.getId(), rejectionAudit);
        notificationService.createNotification(saved.getTimesheet().getUser().getId(),
                "TIMESHEET_REJECTED",
                "Project Timesheet Rejected",
                saved.getProject().getCode() + " for " + saved.getTimesheet().getMonth() + "/" + saved.getTimesheet().getYear()
                        + " was rejected. Reason: " + rejectionReason + ". Correction window ends " + saved.getCorrectionUntil(),
                saved.getId(),
                "TimesheetProjectSubmission");

        return toProjectSubmissionDTO(saved);
    }

    public List<TimesheetDTO> getTimesheetsByStatus(TimesheetStatus status) {
        return timesheetRepository.findByStatus(status).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private void notifyReviewersOfSubmission(Timesheet timesheet) {
        Set<Long> notified = new java.util.HashSet<>();
        getEntryProjectIds(timesheet).stream()
                .map(projectRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(project -> project.getProjectManager())
                .filter(manager -> manager != null && !manager.getId().equals(timesheet.getUser().getId()))
                .forEach(manager -> notifySubmissionTo(timesheet, manager, notified));

        userRepository.findByRole(com.maxwell.chronos.enums.UserRole.ADMIN)
                .forEach(admin -> notifySubmissionTo(timesheet, admin, notified));
    }

    private void notifySubmissionTo(Timesheet timesheet, User user, Set<Long> notified) {
        if (!notified.add(user.getId())) {
            return;
        }
        notificationService.createNotification(user.getId(),
                "TIMESHEET_SUBMITTED",
                "Timesheet Submitted",
                timesheet.getUser().getFullName() + " submitted a timesheet for " + timesheet.getMonth() + "/" + timesheet.getYear(),
                timesheet.getId(),
                "Timesheet");
    }

    private void notifyReviewersOfProjectSubmission(TimesheetProjectSubmission submission) {
        Set<Long> notified = new java.util.HashSet<>();
        User approver = submission.getAssignedApprover();
        if (approver != null) notifyProjectSubmissionTo(submission, approver, notified);
    }

    private void notifyProjectSubmissionTo(TimesheetProjectSubmission submission, User user, Set<Long> notified) {
        if (!notified.add(user.getId())) {
            return;
        }
        notificationService.createNotification(user.getId(),
                "TIMESHEET_SUBMITTED",
                "Project Timesheet Submitted",
                submission.getTimesheet().getUser().getFullName() + " submitted "
                        + submission.getProject().getCode() + " for "
                        + submission.getTimesheet().getMonth() + "/" + submission.getTimesheet().getYear(),
                submission.getId(),
                "TimesheetProjectSubmission");
    }

    private boolean canReviewTimesheet(Timesheet timesheet, Long reviewerId) {
        User reviewer = userRepository.findById(reviewerId).orElse(null);
        if (reviewer == null) {
            return false;
        }
        Set<Long> projectIds = getEntryProjectIds(timesheet);
        return !projectIds.isEmpty() && projectIds.stream().allMatch(projectId -> canReviewProjectForTimesheet(projectId, timesheet, reviewer));
    }

    private void requireProjectSubmissionReviewer(TimesheetProjectSubmission submission, User reviewer, String fallbackReason) {
        access.requireMayReview(submission.getProject().getId(), reviewer.getId(),
                submission.getTimesheet().getUser().getId(), false, fallbackReason);
    }

    private boolean canReviewProjectSubmission(TimesheetProjectSubmission submission, User reviewer) {
        return access.mayReview(submission.getProject().getId(), reviewer.getId(),
                submission.getTimesheet().getUser().getId(), false, "Pending queue preview");
    }

    private boolean canViewProjectSubmission(Timesheet timesheet, Project project, User user) {
        access.requireActiveCompanyAccess(timesheet.getCompanyId(),user.getId());
        if (timesheet.getUser().getId().equals(user.getId()) || access.hasPlatformRole(user.getId(), "PLATFORM_ADMIN")
                || access.mayManageProject(project.getId(), user.getId())) return true;
        boolean belongsToProject = projectAssignmentRepository.findByProjectIdAndUserId(project.getId(), timesheet.getUser().getId()).isPresent()
                || timesheet.getTimeEntries().stream().anyMatch(entry -> entry.getProject() != null && project.getId().equals(entry.getProject().getId()));
        return belongsToProject && canReviewProjectForTimesheet(project.getId(), timesheet, user);
    }

    private boolean canReviewProjectForTimesheet(Long projectId, Timesheet timesheet, User reviewer) {
        if (reviewer == null || timesheet.getUser().getId().equals(reviewer.getId())) {
            return false;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return false;
        }
        return access.mayReview(projectId, reviewer.getId(), timesheet.getUser().getId(), false, "Pending queue preview");
    }

    private void requireCanSubmit(User user) {
        if (access.hasPlatformRole(user.getId(),"PLATFORM_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Platform Admin cannot submit timesheets");
        }
    }

    private TimesheetProjectSubmission getOrCreateProjectSubmission(Timesheet timesheet, Project project) {
        return projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheet.getId(), project.getId())
                .orElseGet(() -> projectSubmissionRepository.save(TimesheetProjectSubmission.builder()
                        .timesheet(timesheet)
                        .project(project)
                        .status(TimesheetStatus.DRAFT)
                        .totalHours(BigDecimal.ZERO)
                        .build()));
    }

    private void syncProjectSubmissionTotals(TimesheetProjectSubmission submission) {
        BigDecimal total = submission.getTimesheet().getTimeEntries().stream()
                .filter(entry -> entry.getProject() != null && entry.getProject().getId().equals(submission.getProject().getId()))
                .map(entry -> entry.getHours() != null ? entry.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        submission.setTotalHours(total);
        projectSubmissionRepository.save(submission);
    }

    private void ensureWithinProjectHourPlan(Timesheet timesheet, Project project, Long entryId, BigDecimal newEntryHours) {
        BigDecimal plannedHours = resolvePlannedHours(timesheet, project);
        BigDecimal existingProjectHours = timesheet.getTimeEntries().stream()
                .filter(entry -> entry.getProject() != null && entry.getProject().getId().equals(project.getId()))
                .filter(entry -> entryId == null || !entry.getId().equals(entryId))
                .map(entry -> entry.getHours() != null ? entry.getHours() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projectedHours = existingProjectHours.add(newEntryHours != null ? newEntryHours : BigDecimal.ZERO);
        if (projectedHours.compareTo(plannedHours) > 0) {
            throw new IllegalArgumentException("Logged hours cannot exceed the planned hours for this project");
        }
    }

    private BigDecimal resolvePlannedHours(Timesheet timesheet, Project project) {
        if (timesheet == null || project == null || timesheet.getUser() == null) {
            return BigDecimal.ZERO;
        }
        var correctionSubmission = projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheet.getId(), project.getId());
        if (correctionSubmission.isPresent() && correctionOpen(timesheet, correctionSubmission.get())
                && correctionSubmission.get().getCorrectionPlannedHours() != null)
            return correctionSubmission.get().getCorrectionPlannedHours();
        return projectAssignmentRepository.findByProjectIdAndUserId(project.getId(), timesheet.getUser().getId())
                .map(assignment -> assignment.getPlannedHours())
                .or(() -> projectHourPlanRepository
                        .findByProjectIdAndUserIdAndYearAndMonth(project.getId(), timesheet.getUser().getId(), timesheet.getYear(), timesheet.getMonth())
                        .map(plan -> plan.getPlannedHours()))
                .orElse(BigDecimal.ZERO);
    }

    private void updateMonthlyStatus(Timesheet timesheet) {
        List<TimesheetProjectSubmission> submissions = projectSubmissionRepository.findByTimesheetId(timesheet.getId()).stream()
                .filter(submission -> submission.getTotalHours() != null && submission.getTotalHours().signum() > 0).toList();
        if (submissions.isEmpty()) {
            timesheet.setStatus(TimesheetStatus.DRAFT);
        } else if (submissions.stream().anyMatch(submission -> TimesheetStatus.REJECTED.equals(submission.getStatus()))) {
            timesheet.setStatus(TimesheetStatus.REJECTED);
        } else if (submissions.stream().anyMatch(submission -> TimesheetStatus.SUBMITTED.equals(submission.getStatus())
                || TimesheetStatus.CHANGE_REQUESTED.equals(submission.getStatus()))) {
            timesheet.setStatus(TimesheetStatus.SUBMITTED);
        } else if (submissions.stream().allMatch(TimesheetProjectSubmission::isPdfExportEligible)) {
            timesheet.setStatus(TimesheetStatus.APPROVED);
            timesheet.setApprovalFrozen(true);
        } else {
            timesheet.setStatus(TimesheetStatus.DRAFT);
        }
        timesheet.setSubmittedAt(submissions.stream()
                .map(TimesheetProjectSubmission::getSubmittedAt)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null));
        timesheet.setApprovedAt(timesheet.getStatus() != TimesheetStatus.APPROVED ? null : submissions.stream()
                .map(TimesheetProjectSubmission::getApprovedAt)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null));
        timesheetRepository.save(timesheet);
    }

    private Set<Long> getEntryProjectIds(Timesheet timesheet) {
        if (timesheet.getTimeEntries() == null) {
            return Set.of();
        }
        return timesheet.getTimeEntries().stream()
                .map(TimeEntry::getProject)
                .filter(java.util.Objects::nonNull)
                .map(project -> project.getId())
                .collect(Collectors.toSet());
    }

    private com.maxwell.chronos.domain.Project requireAssignedProject(Long projectId, Long userId, Timesheet timesheet) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project code is required");
        }
        access.requireMaySubmit(projectId, userId, "timesheets");
        boolean correction = projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheet.getId(), projectId)
                .map(submission -> correctionOpen(timesheet, submission)).orElse(false);
        boolean historicalGrace = YearMonth.of(timesheet.getYear(), timesheet.getMonth()).isBefore(YearMonth.now())
                && standardEditingOpen(timesheet);
        if (!correction && !historicalGrace && !projectService.isAssigned(projectId, userId))
            throw new IllegalArgumentException("Employee is not assigned to this project");
        if (correction || historicalGrace) {
            var assignment = projectAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Project assignment not found"));
            if ((assignment.getStartDate() != null && assignment.getStartDate().isAfter(YearMonth.of(timesheet.getYear(), timesheet.getMonth()).atEndOfMonth()))
                    || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(YearMonth.of(timesheet.getYear(), timesheet.getMonth()).atDay(1))))
                throw new IllegalArgumentException("Project assignment did not cover this timesheet month");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!correction && (project.getStatus() != ProjectStatus.ACTIVE || Boolean.FALSE.equals(project.getIsActive()))) {
            throw new IllegalArgumentException("Hour entry is frozen while this project is " + project.getStatus().name().toLowerCase().replace('_', ' '));
        }
        if (!project.getCompanyId().equals(timesheet.getCompanyId()))
            throw new IllegalArgumentException("Project belongs to a different company calendar");
        return project;
    }

    private boolean correctionOpen(Timesheet timesheet, TimesheetProjectSubmission submission) {
        return submission.getCorrectionUntil() != null
                && submission.getCorrectionUntil().isAfter(java.time.LocalDateTime.now())
                && submission.isEditable();
    }

    private boolean standardEditingOpen(Timesheet timesheet) {
        return standardEditingOpen(timesheet, java.time.LocalDate.now());
    }

    static boolean standardEditingOpen(Timesheet timesheet, java.time.LocalDate today) {
        YearMonth period = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        return !period.isAfter(YearMonth.from(today))
                && !today.isAfter(period.atEndOfMonth().plusDays(EDIT_DAYS_AFTER_MONTH_END));
    }

    private void requireEditingPeriod(Timesheet timesheet, TimesheetProjectSubmission submission) {
        if ((timesheet.isApprovalFrozen() || timesheet.getStatus() == TimesheetStatus.APPROVED
                || timesheet.isLocked()) && !correctionOpen(timesheet, submission)) {
            throw new IllegalArgumentException("This timesheet was approved; request an opening from your Project Admin");
        }
        if (!standardEditingOpen(timesheet) && !correctionOpen(timesheet, submission)) {
            throw new IllegalArgumentException("This timesheet month is closed; request an opening from your Project Admin");
        }
    }

    private void validateEntry(Timesheet timesheet, Project project, LocalDate date, Long entryId,
                               BigDecimal hours, List<TimeEntrySessionDTO> sessions) {
        if (date == null || !YearMonth.from(date).equals(YearMonth.of(timesheet.getYear(), timesheet.getMonth()))) {
            throw new IllegalArgumentException("Entry date must be inside the timesheet month");
        }
        var assignment = projectAssignmentRepository.findByProjectIdAndUserId(project.getId(), timesheet.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Project assignment not found"));
        if ((assignment.getStartDate() != null && date.isBefore(assignment.getStartDate()))
                || (assignment.getEndDate() != null && date.isAfter(assignment.getEndDate()))) {
            throw new IllegalArgumentException("Entry date must be within the project assignment dates");
        }
        BigDecimal otherHours = timesheet.getTimeEntries().stream()
                .filter(e -> date.equals(e.getEntryDate()) && !java.util.Objects.equals(entryId, e.getId()))
                .map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherHours.add(hours).compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Total hours across all projects cannot exceed 24 per day");
        }
        if (sessions == null || sessions.isEmpty()) return;
        long seconds = 0;
        var ordered = new java.util.ArrayList<>(sessions);
        for (var session : ordered) {
            if (session == null || session.getLoginTime() == null || session.getLogoutTime() == null
                    || !session.getLogoutTime().isAfter(session.getLoginTime())) {
                throw new IllegalArgumentException("Each session requires login and logout times in order");
            }
            seconds += java.time.Duration.between(session.getLoginTime(), session.getLogoutTime()).getSeconds();
        }
        ordered.sort(Comparator.comparing(TimeEntrySessionDTO::getLoginTime));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).getLoginTime().isBefore(ordered.get(i - 1).getLogoutTime())) {
                throw new IllegalArgumentException("Time sessions cannot overlap");
            }
        }
        for (var other : timesheet.getTimeEntries()) {
            if (!date.equals(other.getEntryDate()) || java.util.Objects.equals(entryId, other.getId())) continue;
            for (var existing : other.getSessions()) for (var session : ordered) {
                if (session.getLoginTime().isBefore(existing.getLogoutTime())
                        && existing.getLoginTime().isBefore(session.getLogoutTime())) {
                    throw new IllegalArgumentException("Time sessions cannot overlap across projects");
                }
            }
        }
        BigDecimal duration = BigDecimal.valueOf(seconds).divide(new BigDecimal("3600"), 2, java.math.RoundingMode.HALF_UP);
        if (duration.compareTo(hours) != 0) throw new IllegalArgumentException("Hours must match session duration");
    }

    private void applySessions(TimeEntry entry, List<TimeEntrySessionDTO> sessionDTOs) {
        entry.getSessions().clear();
        if (sessionDTOs == null) {
            return;
        }
        int order = 0;
        for (TimeEntrySessionDTO dto : sessionDTOs) {
            if (dto.getLoginTime() == null || dto.getLogoutTime() == null) {
                continue;
            }
            LocalTime login = dto.getLoginTime();
            LocalTime logout = dto.getLogoutTime();
            if (!logout.isAfter(login)) {
                throw new IllegalArgumentException("Logout time must be after login time");
            }
            entry.getSessions().add(TimeEntrySession.builder()
                    .timeEntry(entry)
                    .loginTime(login)
                    .logoutTime(logout)
                    .displayOrder(order++)
                    .build());
        }
    }

    private BigDecimal resolveProjectBillRate(Project project, User user) {
        if (project != null && user != null) {
            return projectAssignmentRepository.findByProjectIdAndUserId(project.getId(), user.getId())
                    .map(assignment -> assignment.getBillRate() != null ? assignment.getBillRate() : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveEffectiveBillRate(Timesheet timesheet) {
        BigDecimal projectRate = resolveProjectBillRate(timesheet.getPrimaryProject(), timesheet.getUser());
        if (projectRate.compareTo(BigDecimal.ZERO) > 0) {
            return projectRate;
        }
        return timesheet.getBillRate() != null ? timesheet.getBillRate() : BigDecimal.ZERO;
    }

    private boolean isApprovedVacationDay(Timesheet timesheet, LocalDate date) {
        return getApprovedVacationDates(timesheet).contains(date);
    }

    private Set<LocalDate> getApprovedVacationDates(Timesheet timesheet) {
        YearMonth yearMonth = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        return getVacationRequestsForMonth(timesheet).stream()
                .filter(vacation -> VacationStatus.APPROVED.equals(vacation.getStatus()) || VacationStatus.LOCKED.equals(vacation.getStatus()))
                .flatMap(vacation -> vacation.getStartDate().datesUntil(vacation.getEndDate().plusDays(1)))
                .filter(date -> !date.isBefore(yearMonth.atDay(1)) && !date.isAfter(yearMonth.atEndOfMonth()))
                .collect(Collectors.toSet());
    }

    private List<VacationRequest> getVacationRequestsForMonth(Timesheet timesheet) {
        YearMonth yearMonth = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        return vacationRequestRepository.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                timesheet.getUser().getId(), yearMonth.atEndOfMonth(), yearMonth.atDay(1)).stream()
                .filter(vacation->Objects.equals(vacation.getCompanyId(),timesheet.getCompanyId())).toList();
    }

    private Set<VacationDayDTO> getVacationDaysForMonth(Timesheet timesheet) {
        YearMonth yearMonth = YearMonth.of(timesheet.getYear(), timesheet.getMonth());
        return getVacationRequestsForMonth(timesheet).stream()
                .filter(vacation -> VacationStatus.SUBMITTED.equals(vacation.getStatus())
                        || VacationStatus.APPROVED.equals(vacation.getStatus())
                        || VacationStatus.LOCKED.equals(vacation.getStatus()))
                .flatMap(vacation -> vacation.getStartDate().datesUntil(vacation.getEndDate().plusDays(1))
                        .filter(date -> !date.isBefore(yearMonth.atDay(1)) && !date.isAfter(yearMonth.atEndOfMonth()))
                        .map(date -> VacationDayDTO.builder()
                                .vacationRequestId(vacation.getId())
                                .date(date)
                                .vacationType(vacation.getVacationType())
                                .status(vacation.getStatus())
                                .build()))
                .sorted(Comparator.comparing(VacationDayDTO::getDate))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal loggedAndApprovedHoursToDate(Long userId, Long projectId) {
        LocalDate today = LocalDate.now();
        BigDecimal logged = timeEntryRepository.sumLoggedHoursToDate(userId, projectId, today,
                List.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED));
        BigDecimal approved = projectSubmissionRepository.findByProjectIdAndTimesheetUserId(projectId, userId).stream()
                .filter(TimesheetProjectSubmission::isPdfExportEligible)
                .filter(s -> s.getApprovedAt() == null || !s.getApprovedAt().toLocalDate().isAfter(today))
                .map(s -> s.getTotalHours() == null ? BigDecimal.ZERO : s.getTotalHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return (logged == null ? BigDecimal.ZERO : logged).add(approved);
    }

    private TimesheetProjectSubmissionDTO reviewDTO(TimesheetProjectSubmission submission, User reviewer) {
        TimesheetProjectSubmissionDTO dto = toProjectSubmissionDTO(submission);
        boolean allowed = reviewer != null && canReviewProjectSubmission(submission, reviewer);
        dto.setReviewAllowed(allowed);
        dto.setFallbackRequired(allowed && access.hasProjectRole(submission.getProject().getId(), reviewer.getId(), "PROJECT_ADMIN")
                && !access.hasProjectRole(submission.getProject().getId(), reviewer.getId(), "PROJECT_MANAGER")
                && !access.hasModeratorGrant(submission.getProject().getId(), reviewer.getId(), false)
                && !access.hasProjectRole(submission.getProject().getId(), submission.getTimesheet().getUser().getId(), "PROJECT_MANAGER"));
        return dto;
    }

    private boolean isProjectAdminFallback(TimesheetProjectSubmission submission, long reviewerId) {
        long projectId = submission.getProject().getId();
        return access.hasProjectRole(projectId, reviewerId, "PROJECT_ADMIN")
                && !access.hasProjectRole(projectId, reviewerId, "PROJECT_MANAGER")
                && !access.hasModeratorGrant(projectId, reviewerId, false)
                && !access.hasProjectRole(projectId, submission.getTimesheet().getUser().getId(), "PROJECT_MANAGER");
    }

    private TimesheetProjectSubmissionDTO toProjectSubmissionDTO(TimesheetProjectSubmission submission) {
        Timesheet timesheet = submission.getTimesheet();
        Project project = submission.getProject();
        Long projectId = project.getId();
        BigDecimal plannedHours = resolvePlannedHours(timesheet, project);
        BigDecimal totalHours = submission.getTotalHours() != null ? submission.getTotalHours() : BigDecimal.ZERO;
        boolean submitterManagesProject = project.getProjectManager() != null
                && project.getProjectManager().getId().equals(timesheet.getUser().getId());
        User routedApprover = submission.getAssignedApprover() != null ? submission.getAssignedApprover()
                : submitterManagesProject ? project.getProjectManagerHoursApprover() : project.getProjectManager();
        return TimesheetProjectSubmissionDTO.builder()
                .id(submission.getId())
                .timesheetId(timesheet.getId())
                .projectId(projectId)
                .projectCode(project.getCode())
                .projectName(project.getName())
                .projectManagerId(project.getProjectManager() != null ? project.getProjectManager().getId() : null)
                .projectManagerName(project.getProjectManager() != null ? project.getProjectManager().getFullName() : null)
                .projectManagerHoursApproverName(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getFullName() : null)
                .routedApproverName(routedApprover != null ? routedApprover.getFullName() : null)
                .routedApproverId(routedApprover != null ? routedApprover.getId() : null)
                .userId(timesheet.getUser().getId())
                .companyId(timesheet.getCompanyId())
                .userName(timesheet.getUser().getFullName())
                .userJobTitle(access.employmentJobTitle(timesheet.getCompanyId(),timesheet.getUser().getId()))
                .year(timesheet.getYear())
                .month(timesheet.getMonth())
                .status(submission.getStatus())
                .totalHours(totalHours)
                .loggedHoursToDate(loggedAndApprovedHoursToDate(timesheet.getUser().getId(), project.getId()))
                .plannedHours(plannedHours)
                .remainingHours(plannedHours.subtract(totalHours).max(BigDecimal.ZERO))
                .billRate(submission.isPdfExportEligible() ? submission.getApprovedBillRate() : resolveProjectBillRate(project, timesheet.getUser()))
                .submittedAt(submission.getSubmittedAt())
                .approvedAt(submission.getApprovedAt())
                .approvedByName(submission.getApprovedBy() != null ? submission.getApprovedBy().getFullName() : null)
                .rejectedAt(submission.getRejectedAt())
                .rejectedByName(submission.getRejectedBy() != null ? submission.getRejectedBy().getFullName() : null)
                .rejectionReason(submission.getRejectionReason())
                .correctionUntil(submission.getCorrectionUntil())
                .openingActive(correctionOpen(timesheet, submission))
                .editable(submission.isEditable())
                .pdfExportEligible(submission.isPdfExportEligible())
                .timeEntries(timesheet.getTimeEntries().stream()
                        .filter(entry -> entry.getProject() != null && entry.getProject().getId().equals(projectId))
                        .sorted(Comparator.comparing(TimeEntry::getEntryDate))
                        .map(this::toTimeEntryDTO)
                        .toList())
                .build();
    }

    private TimesheetDTO toDTO(Timesheet timesheet) {
        return TimesheetDTO.builder()
                .id(timesheet.getId())
                .companyId(timesheet.getCompanyId())
                .userId(timesheet.getUser().getId())
                .userName(timesheet.getUser().getFullName())
                .userJobTitle(access.employmentJobTitle(timesheet.getCompanyId(),timesheet.getUser().getId()))
                .primaryProjectId(timesheet.getPrimaryProject() != null ? timesheet.getPrimaryProject().getId() : null)
                .primaryProjectCode(timesheet.getPrimaryProject() != null ? timesheet.getPrimaryProject().getCode() : null)
                .primaryProjectName(timesheet.getPrimaryProject() != null ? timesheet.getPrimaryProject().getName() : null)
                .year(timesheet.getYear())
                .month(timesheet.getMonth())
                .status(timesheet.getStatus())
                .totalHours(timesheet.getTotalHours())
                .billRate(timesheet.getBillRate())
                .effectiveBillRate(resolveEffectiveBillRate(timesheet))
                .approvedHourlyRate(timesheet.getApprovedHourlyRate())
                .submittedAt(timesheet.getSubmittedAt())
                .approvedAt(timesheet.getApprovedAt())
                .approvalFrozen(timesheet.isApprovalFrozen())
                .approvedByName(timesheet.getApprovedBy() != null ? timesheet.getApprovedBy().getFullName() : null)
                .rejectedAt(timesheet.getRejectedAt())
                .rejectedByName(timesheet.getRejectedBy() != null ? timesheet.getRejectedBy().getFullName() : null)
                .rejectionReason(timesheet.getRejectionReason())
                .editable(timesheet.isEditable())
                .pdfExportEligible(TimesheetStatus.APPROVED.equals(timesheet.getStatus()) || TimesheetStatus.LOCKED.equals(timesheet.getStatus()))
                .createdAt(timesheet.getCreatedAt())
                .updatedAt(timesheet.getUpdatedAt())
                .timeEntries(timesheet.getTimeEntries().stream()
                        .map(this::toTimeEntryDTO)
                        .collect(Collectors.toSet()))
                .vacationDays(getVacationDaysForMonth(timesheet))
                .build();
    }

    private TimeEntryDTO toTimeEntryDTO(TimeEntry entry) {
        return TimeEntryDTO.builder()
                .id(entry.getId())
                .timesheetId(entry.getTimesheet().getId())
                .projectId(entry.getProject() != null ? entry.getProject().getId() : null)
                .projectCode(entry.getProject() != null ? entry.getProject().getCode() : null)
                .projectName(entry.getProject() != null ? entry.getProject().getName() : null)
                .entryDate(entry.getEntryDate())
                .hours(entry.getHours())
                .notes(entry.getNotes())
                .sessions(entry.getSessions().stream()
                        .map(session -> TimeEntrySessionDTO.builder()
                                .id(session.getId())
                                .loginTime(session.getLoginTime())
                                .logoutTime(session.getLogoutTime())
                                .build())
                        .toList())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}
