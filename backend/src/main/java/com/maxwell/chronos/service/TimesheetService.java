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
                com.maxwell.chronos.enums.AuditAction.TIMESHEET_REOPENED);
        return history.stream().filter(event -> actions.contains(event.getAction()))
                .sorted(Comparator.comparing(com.maxwell.chronos.dto.AuditLogDTO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(com.maxwell.chronos.dto.AuditLogDTO::getId, Comparator.reverseOrder()))
                .toList();
    }

    public record WeekCopyDay(LocalDate date, BigDecimal hours, String skippedReason) {}
    public record WeekCopyResult(List<WeekCopyDay> days, int copiedDays) {}

    public WeekCopyResult copyPreviousWeek(Long timesheetId, Long projectId, LocalDate weekStart,
                                           Long userId, boolean apply) {
        Timesheet target = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (!target.getUser().getId().equals(userId))
            throw new org.springframework.security.access.AccessDeniedException("Only the owner can copy hours");
        requireCanSubmit(target.getUser());
        Project project = requireAssignedProject(projectId, userId);
        requireCurrentEditingPeriod(target);
        YearMonth period = YearMonth.of(target.getYear(), target.getMonth());
        if (period.isAfter(YearMonth.now()) || target.isLocked())
            throw new IllegalArgumentException("This timesheet is read-only");
        var submission = projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheetId, projectId);
        if (submission.isPresent() && !submission.get().isEditable())
            throw new IllegalArgumentException("Project timesheet is not editable");
        if (weekStart == null || weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY
                || weekStart.isAfter(period.atEndOfMonth()) || weekStart.plusDays(6).isBefore(period.atDay(1)))
            throw new IllegalArgumentException("Choose a Monday in a week overlapping this month");
        var assignment = projectAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        var leaveDates = getApprovedVacationDates(target);
        var days = new java.util.ArrayList<WeekCopyDay>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = weekStart.plusDays(offset), sourceDate = date.minusWeeks(1);
            if (!YearMonth.from(date).equals(period)) continue;
            var source = timesheetRepository.findByUserIdAndYearAndMonth(userId, sourceDate.getYear(), sourceDate.getMonthValue());
            BigDecimal hours = source.stream().flatMap(t -> t.getTimeEntries().stream())
                    .filter(e -> sourceDate.equals(e.getEntryDate()) && e.getProject() != null && projectId.equals(e.getProject().getId()))
                    .map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            String skip = null;
            if (hours.signum() <= 0) skip = "No hours last week";
            else if (target.getTimeEntries().stream().anyMatch(e -> date.equals(e.getEntryDate())
                    && e.getProject() != null && projectId.equals(e.getProject().getId()))) skip = "Existing entry kept";
            else if (leaveDates.contains(date)) skip = "Approved leave";
            else if ((assignment.getStartDate() != null && date.isBefore(assignment.getStartDate()))
                    || (assignment.getEndDate() != null && date.isAfter(assignment.getEndDate()))) skip = "Outside assignment dates";
            days.add(new WeekCopyDay(date, hours, skip));
        }
        int copied = 0;
        if (apply) {
            for (var day : days) if (day.skippedReason() == null) {
                addTimeEntry(timesheetId, day.date(), day.hours(), null, projectId, List.of(), userId);
                copied++;
            }
        }
        return new WeekCopyResult(days, copied);
    }

    public record MissingTimesheet(Long userId, String userName, Long projectId, String projectCode,
                                   Long timesheetId, String status, BigDecimal hours) {}

    public List<MissingTimesheet> getMissingTimesheets(int year, int month, User reviewer) {
        YearMonth period = YearMonth.of(year, month);
        if (reviewer == null || (!reviewer.isAdmin() && !reviewer.isSuperAdmin() && !projectService.canReviewProjects(reviewer.getId())))
            throw new org.springframework.security.access.AccessDeniedException("Manager permission required");
        var result = new java.util.ArrayList<MissingTimesheet>();
        var visibleProjects = projectService.visibleProjectIds(reviewer);
        var monthly = timesheetRepository.findByYearAndMonth(year, month).stream()
                .collect(Collectors.toMap(t -> t.getUser().getId(), t -> t));
        for (var assignment : projectAssignmentRepository.findAll()) {
            var employee = assignment.getUser();
            var project = assignment.getProject();
            if (!visibleProjects.contains(project.getId()) || !Boolean.TRUE.equals(project.getIsActive())
                    || project.getStatus() != com.maxwell.chronos.enums.ProjectStatus.ACTIVE) continue;
            if (employee.isSuperAdmin() || !Boolean.TRUE.equals(employee.getIsActive())
                    || (assignment.getStartDate() != null && assignment.getStartDate().isAfter(period.atEndOfMonth()))
                    || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(period.atDay(1)))
                    || (!Boolean.TRUE.equals(assignment.getIsActive()) && assignment.getEndDate() == null)) continue;
            Timesheet sheet = monthly.get(employee.getId());
            var submission = sheet == null ? java.util.Optional.<TimesheetProjectSubmission>empty()
                    : projectSubmissionRepository.findByTimesheetIdAndProjectId(sheet.getId(), project.getId());
            var status = submission.map(TimesheetProjectSubmission::getStatus).orElse(TimesheetStatus.DRAFT);
            BigDecimal hours = sheet == null ? BigDecimal.ZERO : sheet.getTimeEntries().stream()
                    .filter(e -> e.getProject() != null && project.getId().equals(e.getProject().getId()))
                    .map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new MissingTimesheet(employee.getId(), employee.getFullName(), project.getId(), project.getCode(),
                    sheet == null ? null : sheet.getId(), status == TimesheetStatus.DRAFT && hours.signum() == 0 ? "NOT_STARTED" : status.name(), hours));
        }
        result.sort(Comparator.comparing(MissingTimesheet::userName).thenComparing(MissingTimesheet::projectCode));
        return result;
    }

    public TimesheetDTO getOrCreateTimesheet(Long userId, int year, int month) {
        YearMonth.of(year, month);
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Timesheet timesheet = timesheetRepository.findPeriodForUpdate(userId, year, month)
                .orElseGet(() -> {
                    Timesheet newTimesheet = Timesheet.builder()
                            .user(user)
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
                .id(sheet.getId()).userId(sheet.getUser().getId()).userName(sheet.getUser().getFullName())
                .userJobTitle(sheet.getUser().getJobTitle()).year(sheet.getYear()).month(sheet.getMonth())
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

    public TimesheetDTO reopenTimesheet(Long timesheetId, String reason, Long reopeningUserId) {
        User reopeningUser = userRepository.findById(reopeningUserId)
                .orElseThrow(() -> new IllegalArgumentException("Reopening user not found"));

        if (!reopeningUser.isSuperAdmin()) {
            throw new IllegalArgumentException("Only Super Admin can reopen timesheets");
        }

        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!timesheet.getStatus().equals(TimesheetStatus.APPROVED) && !timesheet.getStatus().equals(TimesheetStatus.LOCKED)) {
            throw new IllegalArgumentException("Only approved or locked timesheets can be reopened");
        }

        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reopening reason is required");
        for (var submission : projectSubmissionRepository.findByTimesheetId(timesheetId)) {
            submission.setStatus(TimesheetStatus.DRAFT);
            submission.setSubmittedAt(null);
            submission.setApprovedAt(null);
            submission.setApprovedBy(null);
            submission.setApprovedBillRate(null);
            submission.setRejectedAt(null);
            submission.setRejectedBy(null);
            submission.setRejectionReason(null);
            projectSubmissionRepository.save(submission);
        }
        timesheet.setApprovedHourlyRate(null);
        timesheet.setSubmittedAt(null);
        timesheet.setRejectedAt(null);
        timesheet.setRejectedBy(null);
        timesheet.setRejectionReason(null);
        timesheet.setStatus(TimesheetStatus.DRAFT);
        timesheet.setApprovedAt(null);
        timesheet.setApprovedBy(null);
        Timesheet saved = timesheetRepository.save(timesheet);

        auditService.logAction(reopeningUserId, "TIMESHEET_REOPENED", "Timesheet", timesheetId,
                "Reason: " + reason);

        // Notify employee
        notificationService.createNotification(saved.getUser().getId(),
                "TIMESHEET_REOPENED",
                "Timesheet Reopened",
                "Your " + saved.getMonth() + "/" + saved.getYear() + " timesheet has been reopened.",
                timesheetId,
                "Timesheet");

        return toDTO(saved);
    }

    public TimeEntryDTO addTimeEntry(Long timesheetId, LocalDate entryDate, BigDecimal hours, String notes,
                                     Long projectId, List<TimeEntrySessionDTO> sessions, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));

        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot edit another user's timesheet");
        }

        var project = requireAssignedProject(projectId, userId);
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, project);
        if (java.time.YearMonth.of(timesheet.getYear(), timesheet.getMonth()).isAfter(java.time.YearMonth.now())) {
            throw new IllegalArgumentException("Future timesheets are read-only until that month begins");
        }
        requireCurrentEditingPeriod(timesheet);
        if (timesheet.isLocked() || !submission.isEditable()) {
            throw new IllegalArgumentException("Project timesheet is not editable");
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

        Project project = requireAssignedProject(projectId, userId);
        if (entry.getProject() != null) requireAssignedProject(entry.getProject().getId(), userId);
        TimesheetProjectSubmission existingSubmission = getOrCreateProjectSubmission(timesheet, entry.getProject() != null ? entry.getProject() : project);
        TimesheetProjectSubmission targetSubmission = getOrCreateProjectSubmission(timesheet, project);
        requireCurrentEditingPeriod(timesheet);
        if (timesheet.isLocked() || !existingSubmission.isEditable() || !targetSubmission.isEditable()) {
            throw new IllegalArgumentException("Project timesheet is not editable");
        }
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
        requireAssignedProject(entry.getProject().getId(), userId);
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, entry.getProject());
        requireCurrentEditingPeriod(timesheet);
        if (timesheet.isLocked() || !submission.isEditable()) {
            throw new IllegalArgumentException("Project timesheet is not editable");
        }

        timesheet.getTimeEntries().remove(entry);
        timeEntryRepository.delete(entry);
        timesheet.calculateTotalHours();
        timesheetRepository.save(timesheet);
        syncProjectSubmissionTotals(submission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_EDITED", "Timesheet", timesheetId,
                "Deleted entry " + entryId);
    }

    public List<TimesheetDTO> getTimesheetsByUser(Long userId) {
        return timesheetRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TimesheetDTO> getPendingTimesheets(User reviewer) {
        if (reviewer == null || (!reviewer.isSuperAdmin() && !reviewer.isAdmin() && !projectService.canReviewProjects(reviewer.getId()))) {
            throw new IllegalArgumentException("Reviewer permission required");
        }
        List<Timesheet> submitted = timesheetRepository.findByStatus(TimesheetStatus.SUBMITTED);
        List<Timesheet> changeRequests = timesheetRepository.findByStatus(TimesheetStatus.CHANGE_REQUESTED);
        return java.util.stream.Stream.concat(submitted.stream(), changeRequests.stream())
                .filter(timesheet -> reviewer.isSuperAdmin() || reviewer.isAdmin() || canReviewTimesheet(timesheet, reviewer.getId()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TimesheetProjectSubmissionDTO> getPendingProjectSubmissions(User reviewer) {
        if (reviewer == null || (!reviewer.isAdmin() && !reviewer.isSuperAdmin() && !projectService.canReviewProjects(reviewer.getId()))) {
            throw new IllegalArgumentException("Reviewer permission required");
        }
        List<TimesheetProjectSubmission> submitted = projectSubmissionRepository.findByStatus(TimesheetStatus.SUBMITTED);
        List<TimesheetProjectSubmission> changeRequests = projectSubmissionRepository.findByStatus(TimesheetStatus.CHANGE_REQUESTED);
        return java.util.stream.Stream.concat(submitted.stream(), changeRequests.stream())
                .filter(submission -> canReviewProjectSubmission(submission, reviewer)
                        && !submission.getTimesheet().getUser().getId().equals(reviewer.getId()))
                .map(this::toProjectSubmissionDTO)
                .collect(Collectors.toList());
    }

    public TimesheetProjectSubmissionDTO getProjectSubmission(Long timesheetId, Long projectId, User requestingUser) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!canViewProjectSubmission(timesheet, project, requestingUser)) {
            throw new IllegalArgumentException("User cannot view this project timesheet");
        }

        return toProjectSubmissionDTO(projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheetId, projectId)
                .orElseGet(() -> TimesheetProjectSubmission.builder().timesheet(timesheet).project(project)
                        .status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ZERO).build()));
    }

    public TimesheetProjectSubmissionDTO submitProjectTimesheet(Long timesheetId, Long projectId, Long userId) {
        Timesheet timesheet = timesheetRepository.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (!timesheet.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot submit another user's timesheet");
        }
        requireCanSubmit(timesheet.getUser());

        Project project = requireAssignedProject(projectId, userId);
        TimesheetProjectSubmission submission = getOrCreateProjectSubmission(timesheet, project);
        requireCurrentEditingPeriod(timesheet);
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
        submission.setStatus(TimesheetStatus.SUBMITTED);
        submission.setSubmittedAt(java.time.LocalDateTime.now());
        submission.setApprovedAt(null);
        submission.setApprovedBy(null);
        submission.setRejectedAt(null);
        submission.setRejectedBy(null);
        submission.setRejectionReason(null);
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(timesheet);

        auditService.logAction(userId, "TIMESHEET_SUBMITTED", "TimesheetProjectSubmission", saved.getId(),
                "Project: " + project.getCode() + ", Year: " + timesheet.getYear() + ", Month: " + timesheet.getMonth());

        notifyReviewersOfProjectSubmission(saved);
        return toProjectSubmissionDTO(saved);
    }

    public TimesheetProjectSubmissionDTO approveProjectSubmission(Long submissionId, Long approvingUserId) {
        User approvingUser = userRepository.findById(approvingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Approving user not found"));
        Long parentId = projectSubmissionRepository.findTimesheetId(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
        timesheetRepository.findForUpdate(parentId).orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));

        requireProjectSubmissionReviewer(submission, approvingUser);
        if (submission.getTimesheet().getUser().getId().equals(approvingUserId)) {
            throw new IllegalArgumentException("Users cannot approve their own timesheets");
        }
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
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(saved.getTimesheet());

        auditService.logAction(approvingUserId, "TIMESHEET_APPROVED", "TimesheetProjectSubmission", saved.getId(),
                "Employee: " + saved.getTimesheet().getUser().getFullName() + ", Project: " + saved.getProject().getCode());
        notificationService.createNotification(saved.getTimesheet().getUser().getId(),
                "TIMESHEET_APPROVED",
                "Project Timesheet Approved",
                saved.getProject().getCode() + " for " + saved.getTimesheet().getMonth() + "/" + saved.getTimesheet().getYear() + " has been approved.",
                saved.getId(),
                "TimesheetProjectSubmission");

        return toProjectSubmissionDTO(saved);
    }

    public TimesheetProjectSubmissionDTO rejectProjectSubmission(Long submissionId, String rejectionReason, Long rejectingUserId) {
        User rejectingUser = userRepository.findById(rejectingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Rejecting user not found"));
        Long parentId = projectSubmissionRepository.findTimesheetId(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));
        timesheetRepository.findForUpdate(parentId).orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        TimesheetProjectSubmission submission = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project timesheet not found"));

        requireProjectSubmissionReviewer(submission, rejectingUser);
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
        submission.setApprovedAt(null);
        submission.setApprovedBy(null);
        TimesheetProjectSubmission saved = projectSubmissionRepository.save(submission);
        updateMonthlyStatus(saved.getTimesheet());

        auditService.logAction(rejectingUserId, "TIMESHEET_REJECTED", "TimesheetProjectSubmission", saved.getId(),
                "Reason: " + rejectionReason);
        notificationService.createNotification(saved.getTimesheet().getUser().getId(),
                "TIMESHEET_REJECTED",
                "Project Timesheet Rejected",
                saved.getProject().getCode() + " for " + saved.getTimesheet().getMonth() + "/" + saved.getTimesheet().getYear()
                        + " was rejected. Reason: " + rejectionReason,
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

        userRepository.findByRole(com.maxwell.chronos.enums.UserRole.SUPER_ADMIN)
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
        User manager = submission.getProject().getProjectManager();
        User submitter = submission.getTimesheet().getUser();
        User managerHoursApprover = submission.getProject().getProjectManagerHoursApprover();
        if (manager != null && manager.getId().equals(submitter.getId()) && managerHoursApprover != null
                && !managerHoursApprover.getId().equals(submitter.getId())) {
            notifyProjectSubmissionTo(submission, managerHoursApprover, notified);
        } else if (manager != null && !manager.getId().equals(submitter.getId())) {
            notifyProjectSubmissionTo(submission, manager, notified);
        }
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

    private void requireTimesheetReviewer(Timesheet timesheet, User reviewer) {
        if (reviewer.isSuperAdmin() || reviewer.isAdmin()) {
            return;
        }
        if (canReviewTimesheet(timesheet, reviewer.getId())) {
            return;
        }
        throw new IllegalArgumentException("Reviewer cannot approve timesheets for these projects");
    }

    private boolean canReviewTimesheet(Timesheet timesheet, Long reviewerId) {
        User reviewer = userRepository.findById(reviewerId).orElse(null);
        if (reviewer == null) {
            return false;
        }
        Set<Long> projectIds = getEntryProjectIds(timesheet);
        return !projectIds.isEmpty() && projectIds.stream().allMatch(projectId -> canReviewProjectForTimesheet(projectId, timesheet, reviewer));
    }

    private void requireProjectSubmissionReviewer(TimesheetProjectSubmission submission, User reviewer) {
        if (canReviewProjectSubmission(submission, reviewer)) {
            return;
        }
        throw new IllegalArgumentException("Reviewer cannot approve timesheets for this project");
    }

    private boolean canReviewProjectSubmission(TimesheetProjectSubmission submission, User reviewer) {
        User submitter = submission.getTimesheet().getUser();
        Long projectId = submission.getProject().getId();
        if (submitter.getId().equals(reviewer.getId())) {
            return false;
        }
        if (reviewer.isSuperAdmin()) {
            return false;
        }
        boolean submitterManagesProject = submission.getProject().getProjectManager() != null
                && submission.getProject().getProjectManager().getId().equals(submitter.getId());
        if (submitterManagesProject) {
            return projectService.canApproveProjectManagerHours(projectId, reviewer.getId());
        }
        return projectService.managesProject(projectId, reviewer.getId());
    }

    private boolean canViewProjectSubmission(Timesheet timesheet, Project project, User user) {
        if (timesheet.getUser().getId().equals(user.getId()) || user.isSuperAdmin() || user.isAdmin()) return true;
        boolean belongsToProject = projectAssignmentRepository.findByProjectIdAndUserId(project.getId(), timesheet.getUser().getId()).isPresent()
                || timesheet.getTimeEntries().stream().anyMatch(entry -> entry.getProject() != null && project.getId().equals(entry.getProject().getId()));
        return belongsToProject && ((projectService.canReviewProjects(user.getId()) && projectService.visibleProjectIds(user).contains(project.getId()))
                || canReviewProjectForTimesheet(project.getId(), timesheet, user));
    }

    private boolean canReviewProjectForTimesheet(Long projectId, Timesheet timesheet, User reviewer) {
        if (reviewer == null || timesheet.getUser().getId().equals(reviewer.getId())) {
            return false;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return false;
        }
        boolean submitterManagesProject = project.getProjectManager() != null
                && project.getProjectManager().getId().equals(timesheet.getUser().getId());
        if (submitterManagesProject) {
            return projectService.canApproveProjectManagerHours(projectId, reviewer.getId());
        }
        return projectService.managesProject(projectId, reviewer.getId());
    }

    private void requireCanSubmit(User user) {
        if (user.isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("SuperAdmin cannot submit timesheets");
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

    private com.maxwell.chronos.domain.Project requireAssignedProject(Long projectId, Long userId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project code is required");
        }
        if (!projectService.isAssigned(projectId, userId)) {
            throw new IllegalArgumentException("Employee is not assigned to this project");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (project.getStatus() != ProjectStatus.ACTIVE || Boolean.FALSE.equals(project.getIsActive())) {
            throw new IllegalArgumentException("Hour entry is frozen while this project is " + project.getStatus().name().toLowerCase().replace('_', ' '));
        }
        return project;
    }

    private void requireCurrentEditingPeriod(Timesheet timesheet) {
        if (!YearMonth.of(timesheet.getYear(), timesheet.getMonth()).equals(YearMonth.now())) {
            throw new IllegalArgumentException("Only the current month's timesheet can be edited or submitted");
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
                timesheet.getUser().getId(), yearMonth.atEndOfMonth(), yearMonth.atDay(1));
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

    private TimesheetProjectSubmissionDTO toProjectSubmissionDTO(TimesheetProjectSubmission submission) {
        Timesheet timesheet = submission.getTimesheet();
        Project project = submission.getProject();
        Long projectId = project.getId();
        BigDecimal plannedHours = resolvePlannedHours(timesheet, project);
        BigDecimal totalHours = submission.getTotalHours() != null ? submission.getTotalHours() : BigDecimal.ZERO;
        boolean submitterManagesProject = project.getProjectManager() != null
                && project.getProjectManager().getId().equals(timesheet.getUser().getId());
        User routedApprover = submitterManagesProject ? project.getProjectManagerHoursApprover() : project.getProjectManager();
        return TimesheetProjectSubmissionDTO.builder()
                .id(submission.getId())
                .timesheetId(timesheet.getId())
                .projectId(projectId)
                .projectCode(project.getCode())
                .projectName(project.getName())
                .projectManagerName(project.getProjectManager() != null ? project.getProjectManager().getFullName() : null)
                .projectManagerHoursApproverName(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getFullName() : null)
                .routedApproverName(routedApprover != null ? routedApprover.getFullName() : null)
                .routedApproverId(routedApprover != null ? routedApprover.getId() : null)
                .userId(timesheet.getUser().getId())
                .userName(timesheet.getUser().getFullName())
                .userJobTitle(timesheet.getUser().getJobTitle())
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
                .userId(timesheet.getUser().getId())
                .userName(timesheet.getUser().getFullName())
                .userJobTitle(timesheet.getUser().getJobTitle())
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
