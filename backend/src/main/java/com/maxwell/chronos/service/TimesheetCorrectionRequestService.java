package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.TimesheetCorrectionRequestDTO;
import com.maxwell.chronos.enums.TimesheetCorrectionStatus;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TimesheetCorrectionRequestService {
    public static final int REQUEST_DAYS_AFTER_MONTH_END = 30;

    private final TimesheetCorrectionRequestRepository requests;
    private final TimesheetRepository timesheets;
    private final TimesheetProjectSubmissionRepository submissions;
    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final UserRepository users;
    private final TimesheetService timesheetService;
    private final NotificationService notifications;
    private final AuditService audit;

    public TimesheetCorrectionRequestDTO request(Long timesheetId, Long projectId, String comment, User employee) {
        if (employee == null || employee.isAdmin()) throw new AccessDeniedException("Employee access required");
        String reason = requiredComment(comment, "Explain why this timesheet needs to be opened");
        Timesheet sheet = timesheets.findForUpdate(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (!sheet.getUser().getId().equals(employee.getId())) throw new AccessDeniedException("Not your timesheet");
        YearMonth period = YearMonth.of(sheet.getYear(), sheet.getMonth());
        if (period.isAfter(YearMonth.now()))
            throw new IllegalArgumentException("Future timesheets cannot be opened");
        LocalDate deadline = period.atEndOfMonth().plusDays(REQUEST_DAYS_AFTER_MONTH_END);
        if (LocalDate.now().isAfter(deadline))
            throw new IllegalArgumentException("The opening request deadline was " + deadline + ".");
        Project project = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        var assignment = assignments.findByProjectIdAndUserId(projectId, employee.getId())
                .orElseThrow(() -> new IllegalArgumentException("You were not assigned to this project"));
        if ((assignment.getStartDate() != null && assignment.getStartDate().isAfter(period.atEndOfMonth()))
                || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(period.atDay(1))))
            throw new IllegalArgumentException("Your assignment did not cover this timesheet month");
        var submission = submissions.findByTimesheetIdAndProjectId(timesheetId, projectId);
        TimesheetStatus projectStatus = submission.map(TimesheetProjectSubmission::getStatus)
                .orElse(TimesheetStatus.DRAFT);
        boolean approved = projectStatus == TimesheetStatus.APPROVED || projectStatus == TimesheetStatus.LOCKED
                || sheet.isApprovalFrozen() || sheet.getStatus() == TimesheetStatus.APPROVED || sheet.isLocked();
        if (!approved && !LocalDate.now().isAfter(period.atEndOfMonth()
                .plusDays(TimesheetService.EDIT_DAYS_AFTER_MONTH_END)))
            throw new IllegalArgumentException("This timesheet is still open for editing");
        if (submission.isPresent()) {
            TimesheetStatus status = submission.get().getStatus();
            if (status == TimesheetStatus.SUBMITTED || status == TimesheetStatus.CHANGE_REQUESTED)
                throw new IllegalArgumentException("This project timesheet is already awaiting review");
            if (submission.get().getCorrectionUntil() != null
                    && submission.get().getCorrectionUntil().isAfter(LocalDateTime.now())
                    && submission.get().isEditable())
                throw new IllegalArgumentException("A correction window is already open");
        }
        if (requests.existsByTimesheetIdAndProjectIdAndStatus(timesheetId, projectId, TimesheetCorrectionStatus.PENDING))
            throw new IllegalArgumentException("An opening request is already pending for this project and month");

        TimesheetCorrectionRequest saved = requests.save(TimesheetCorrectionRequest.builder()
                .timesheet(sheet).project(project).user(employee).status(TimesheetCorrectionStatus.PENDING)
                .employeeComment(reason).build());
        audit.logAction(employee.getId(), "TIMESHEET_CORRECTION_REQUESTED", "TimesheetCorrectionRequest", saved.getId(),
                "Project " + project.getCode() + ", period " + period + ": " + reason);
        for (User projectAdmin : users.findByRole(UserRole.PROJECT_ADMIN)) {
            if (Boolean.TRUE.equals(projectAdmin.getIsActive()) && !projectAdmin.getId().equals(employee.getId()))
                notifications.createNotification(projectAdmin.getId(), "TIMESHEET_CORRECTION_REQUESTED", "Timesheet opening requested",
                        employee.getFullName() + " requested " + project.getCode() + " for " + period + ".",
                        saved.getId(), "TimesheetCorrectionRequest");
        }
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<TimesheetCorrectionRequestDTO> history(Long timesheetId, Long projectId, User requester) {
        Timesheet sheet = timesheets.findById(timesheetId)
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        if (requester == null || (!requester.isProjectAdmin() && !sheet.getUser().getId().equals(requester.getId())))
            throw new AccessDeniedException("Cannot view these opening requests");
        return requests.findByTimesheetIdAndProjectIdOrderByCreatedAtDesc(timesheetId, projectId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<TimesheetCorrectionRequestDTO> pending(User projectAdmin) {
        requireProjectAdmin(projectAdmin);
        return requests.findByStatusOrderByCreatedAtAsc(TimesheetCorrectionStatus.PENDING)
                .stream().map(this::toDTO).toList();
    }

    public TimesheetCorrectionRequestDTO decide(Long requestId, boolean approve, String comment, User projectAdmin) {
        requireProjectAdmin(projectAdmin);
        TimesheetCorrectionRequest found = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Opening request not found"));
        timesheets.findForUpdate(found.getTimesheet().getId())
                .orElseThrow(() -> new IllegalArgumentException("Timesheet not found"));
        TimesheetCorrectionRequest request = requests.findForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Opening request not found"));
        if (request.getStatus() != TimesheetCorrectionStatus.PENDING)
            throw new IllegalArgumentException("This opening request has already been decided");
        if (request.getUser().getId().equals(projectAdmin.getId()))
            throw new AccessDeniedException("You cannot decide your own opening request");
        String note = approve ? optionalComment(comment) : requiredComment(comment, "Explain why the request was declined");
        if (approve) {
            timesheetService.openAfterApprovedRequest(request.getTimesheet().getId(), request.getProject().getId(),
                    note == null ? "Approved employee opening request " + requestId : note, projectAdmin.getId());
        }
        request.setStatus(approve ? TimesheetCorrectionStatus.APPROVED : TimesheetCorrectionStatus.DECLINED);
        request.setAdminComment(note);
        request.setDecidedBy(projectAdmin);
        request.setDecidedAt(LocalDateTime.now());
        requests.save(request);
        audit.logAction(projectAdmin.getId(), approve ? "TIMESHEET_CORRECTION_APPROVED" : "TIMESHEET_CORRECTION_DECLINED",
                "TimesheetCorrectionRequest", requestId, note == null ? "Approved" : note);
        if (!approve) notifications.createNotification(request.getUser().getId(),
                "TIMESHEET_CORRECTION_DECLINED", "Timesheet opening declined",
                "Your timesheet opening request was declined. Reason: " + note,
                requestId, "TimesheetCorrectionRequest");
        return toDTO(request);
    }

    private void requireProjectAdmin(User user) {
        if (user == null || !user.isProjectAdmin()) throw new AccessDeniedException("Project Admin access required");
    }

    private String requiredComment(String value, String message) {
        String trimmed = optionalComment(value);
        if (trimmed == null) throw new IllegalArgumentException(message);
        return trimmed;
    }

    private String optionalComment(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 500) throw new IllegalArgumentException("Comments cannot exceed 500 characters");
        return trimmed;
    }

    private TimesheetCorrectionRequestDTO toDTO(TimesheetCorrectionRequest request) {
        return new TimesheetCorrectionRequestDTO(request.getId(), request.getTimesheet().getId(),
                request.getProject().getId(), request.getProject().getCode(), request.getUser().getId(),
                request.getUser().getFullName(), request.getTimesheet().getYear(), request.getTimesheet().getMonth(),
                request.getStatus(), request.getEmployeeComment(), request.getAdminComment(), request.getCreatedAt(),
                request.getDecidedAt());
    }
}
