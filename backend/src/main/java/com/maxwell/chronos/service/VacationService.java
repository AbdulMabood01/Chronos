package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.domain.VacationRequest;
import com.maxwell.chronos.dto.VacationRequestDTO;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.enums.VacationType;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.VacationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class VacationService {
    private final VacationRequestRepository vacationRequestRepository;
    private final UserRepository userRepository;
    private final TimesheetRepository timesheetRepository;
    private final com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository projectSubmissionRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public VacationRequestDTO createVacationRequest(Long userId, LocalDate startDate, LocalDate endDate, 
                                                   VacationType vacationType, String notes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        VacationRequest vacation = VacationRequest.builder()
                .user(user)
                .startDate(startDate)
                .endDate(endDate)
                .vacationType(vacationType)
                .hours(calculateWorkingDays(startDate, endDate))
                .status(VacationStatus.DRAFT)
                .notes(notes)
                .build();

        VacationRequest saved = vacationRequestRepository.save(vacation);
        auditService.logAction(userId, "VACATION_CREATED", "VacationRequest", saved.getId(),
                "From " + startDate + " to " + endDate + ", Type: " + vacationType);

        return toDTO(saved);
    }

    public VacationRequestDTO updateVacationRequest(Long vacationId, LocalDate startDate, LocalDate endDate,
                                                   VacationType vacationType, String notes, Long userId) {
        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("Vacation request not found"));

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        if (!vacation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot edit another user's vacation request");
        }

        if (!vacation.isEditable()) {
            throw new IllegalArgumentException("Vacation request is not editable");
        }

        vacation.setStartDate(startDate);
        vacation.setEndDate(endDate);
        vacation.setVacationType(vacationType);
        vacation.setHours(calculateWorkingDays(startDate, endDate));
        vacation.setNotes(notes);
        vacation.setStatus(VacationStatus.DRAFT);
        vacation.setSubmittedAt(null);
        vacation.setApprovedAt(null);
        vacation.setApprovedBy(null);
        vacation.setRejectedAt(null);
        vacation.setRejectedBy(null);
        vacation.setRejectionReason(null);

        VacationRequest saved = vacationRequestRepository.save(vacation);
        auditService.logAction(userId, "VACATION_EDITED", "VacationRequest", vacationId,
                "Updated dates and type");

        return toDTO(saved);
    }

    public void deleteVacationRequest(Long vacationId, Long userId) {
        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("Vacation request not found"));

        if (!vacation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot delete another user's vacation request");
        }

        if (!vacation.isEditable()) {
            throw new IllegalArgumentException("Only draft or rejected vacation requests can be deleted");
        }

        vacationRequestRepository.delete(vacation);
        auditService.logAction(userId, "VACATION_EDITED", "VacationRequest", vacationId,
                "Deleted vacation request from " + vacation.getStartDate() + " to " + vacation.getEndDate());
    }

    public VacationRequestDTO submitVacationRequest(Long vacationId, Long userId) {
        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("Vacation request not found"));

        if (!vacation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("User cannot submit another user's vacation request");
        }
        if (vacation.getUser().isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Admin cannot submit vacation requests");
        }

        if (!vacation.isEditable()) {
            throw new IllegalArgumentException("Vacation request cannot be submitted from its current status");
        }

        vacation.setStatus(VacationStatus.SUBMITTED);
        vacation.setSubmittedAt(java.time.LocalDateTime.now());
        VacationRequest saved = vacationRequestRepository.save(vacation);

        auditService.logAction(userId, "VACATION_SUBMITTED", "VacationRequest", vacationId,
                "From " + saved.getStartDate() + " to " + saved.getEndDate());

        notifyAdminsOfVacationSubmission(saved);
        notifyProjectManagers(saved, "VACATION_SUBMITTED", "Vacation Request Submitted", " submitted a vacation request");

        return toDTO(saved);
    }

    public VacationRequestDTO approveVacationRequest(Long vacationId, Long approvingUserId) {
        User approvingUser = userRepository.findById(approvingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Approving user not found"));

        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("Vacation request not found"));
        requireVacationReviewer(vacation, approvingUser);

        if (!vacation.getStatus().equals(VacationStatus.SUBMITTED)) {
            throw new IllegalArgumentException("Only submitted vacation requests can be approved");
        }

        userRepository.findForUpdate(vacation.getUser().getId()).orElseThrow();
        vacation.setStatus(VacationStatus.APPROVED);
        vacation.setApprovedAt(java.time.LocalDateTime.now());
        vacation.setApprovedBy(approvingUser);
        VacationRequest saved = vacationRequestRepository.save(vacation);
        zeroApprovedVacationHours(saved);

        auditService.logAction(approvingUserId, "VACATION_APPROVED", "VacationRequest", vacationId,
                "Employee: " + saved.getUser().getFullName());

        // Notify employee
        notificationService.createNotification(saved.getUser().getId(),
                "VACATION_APPROVED",
                "Vacation Request Approved",
                "Your vacation request from " + saved.getStartDate() + " to " + saved.getEndDate() + " has been approved.",
                vacationId,
                "VacationRequest");
        notifyProjectManagers(saved, "VACATION_APPROVED", "Vacation Request Approved", " has an approved vacation request");

        return toDTO(saved);
    }

    public VacationRequestDTO rejectVacationRequest(Long vacationId, String rejectionReason, Long rejectingUserId) {
        User rejectingUser = userRepository.findById(rejectingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Rejecting user not found"));

        VacationRequest vacation = vacationRequestRepository.findById(vacationId)
                .orElseThrow(() -> new IllegalArgumentException("Vacation request not found"));
        requireVacationReviewer(vacation, rejectingUser);

        if (!vacation.getStatus().equals(VacationStatus.SUBMITTED)) {
            throw new IllegalArgumentException("Only submitted vacation requests can be rejected");
        }

        vacation.setStatus(VacationStatus.REJECTED);
        vacation.setRejectedAt(java.time.LocalDateTime.now());
        vacation.setRejectedBy(rejectingUser);
        vacation.setRejectionReason(rejectionReason);
        VacationRequest saved = vacationRequestRepository.save(vacation);

        auditService.logAction(rejectingUserId, "VACATION_REJECTED", "VacationRequest", vacationId,
                "Reason: " + rejectionReason);

        // Notify employee
        notificationService.createNotification(saved.getUser().getId(),
                "VACATION_REJECTED",
                "Vacation Request Rejected",
                "Your vacation request from " + saved.getStartDate() + " to " + saved.getEndDate() + " was rejected. Reason: " + rejectionReason,
                vacationId,
                "VacationRequest");

        return toDTO(saved);
    }

    public List<VacationRequestDTO> getVacationRequestsByUser(Long userId) {
        return vacationRequestRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VacationRequestDTO> getPendingVacationRequests() {
        return vacationRequestRepository.findByStatus(VacationStatus.SUBMITTED).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VacationRequestDTO> getPendingVacationRequests(User reviewer) {
        if (reviewer == null) {
            throw new IllegalArgumentException("Vacation reviewer permission required");
        }
        return vacationRequestRepository.findByStatus(VacationStatus.SUBMITTED).stream()
                .filter(vacation -> canReviewVacation(vacation, reviewer))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<VacationRequestDTO> getVacationRequestsByStatus(VacationStatus status) {
        return vacationRequestRepository.findByStatus(status).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        // Simplified: assume 5 day work week
        long weeks = totalDays / 7;
        long remainingDays = totalDays % 7;
        long workingDays = (weeks * 5);
        
        // Add remaining days, excluding Saturdays (6) and Sundays (7)
        LocalDate current = startDate.plusWeeks(weeks * 7 / 7);
        for (int i = 0; i < remainingDays; i++) {
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek < 6) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        return new BigDecimal(workingDays * 8); // 8 hours per day
    }

    private void zeroApprovedVacationHours(VacationRequest vacation) {
        Set<YearMonth> affectedMonths = vacation.getStartDate()
                .datesUntil(vacation.getEndDate().plusDays(1))
                .map(YearMonth::from)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<LocalDate> vacationDates = vacation.getStartDate()
                .datesUntil(vacation.getEndDate().plusDays(1))
                .collect(Collectors.toSet());

        for (YearMonth month : affectedMonths) {
            timesheetRepository.findPeriodForUpdate(vacation.getUser().getId(), month.getYear(), month.getMonthValue())
                    .ifPresent(timesheet -> zeroMatchingEntries(timesheet, vacationDates));
        }
    }

    private void zeroMatchingEntries(Timesheet timesheet, Set<LocalDate> vacationDates) {
        boolean changed = false;
        if (timesheet.getTimeEntries() != null) {
            for (var entry : timesheet.getTimeEntries()) {
                if (vacationDates.contains(entry.getEntryDate())
                        && entry.getHours() != null
                        && entry.getHours().compareTo(BigDecimal.ZERO) != 0) {
                    var submission = entry.getProject() == null ? null : projectSubmissionRepository
                            .findByTimesheetIdAndProjectId(timesheet.getId(), entry.getProject().getId()).orElse(null);
                    if (!timesheet.isEditable() || (submission != null && !submission.isEditable())) {
                        throw new IllegalArgumentException("Vacation conflicts with submitted or finalized hours; reopen the timesheet first");
                    }
                    entry.setHours(BigDecimal.ZERO);
                    entry.getSessions().clear();
                    changed = true;
                }
            }
        }

        if (changed) {
            timesheet.calculateTotalHours();
            timesheetRepository.save(timesheet);
            for (var submission : projectSubmissionRepository.findByTimesheetId(timesheet.getId())) {
                submission.setTotalHours(timesheet.getTimeEntries().stream()
                        .filter(e -> e.getProject() != null && e.getProject().getId().equals(submission.getProject().getId()))
                        .map(e -> e.getHours()).reduce(BigDecimal.ZERO, BigDecimal::add));
                projectSubmissionRepository.save(submission);
            }
            auditService.logAction(timesheet.getUser().getId(), "TIMESHEET_EDITED", "Timesheet", timesheet.getId(),
                    "Cleared draft hours and sessions for approved vacation dates");
        }
    }

    private void notifyAdminsOfVacationSubmission(VacationRequest vacation) {
        List<User> reviewers = userRepository.findByRole(com.maxwell.chronos.enums.UserRole.SUPER_ADMIN);
        for (User reviewer : reviewers) {
            notificationService.createNotification(reviewer.getId(),
                    "VACATION_SUBMITTED",
                    "Vacation Request Submitted",
                    vacation.getUser().getFullName() + " submitted a vacation request from " + vacation.getStartDate() + " to " + vacation.getEndDate(),
                    vacation.getId(),
                    "VacationRequest");
        }
    }

    private void requireVacationReviewer(VacationRequest vacation, User reviewer) {
        if (!canReviewVacation(vacation, reviewer)) {
            throw new IllegalArgumentException("Reviewer cannot approve this vacation request");
        }
    }

    private boolean canReviewVacation(VacationRequest vacation, User reviewer) {
        if (vacation == null || reviewer == null || vacation.getUser().getId().equals(reviewer.getId())) {
            return false;
        }
        return reviewer.isSuperAdmin();
    }

    private void notifyProjectManagers(VacationRequest vacation, String type, String title, String action) {
        for (User manager : findProjectManagers(vacation)) {
            if (type.equals("VACATION_SUBMITTED") && manager.isSuperAdmin()) {
                continue;
            }
            notificationService.createNotification(manager.getId(), type, title,
                    vacation.getUser().getFullName() + action + " from " + vacation.getStartDate()
                            + " to " + vacation.getEndDate(), vacation.getId(), "VacationRequest");
        }
    }

    private List<User> findProjectManagers(VacationRequest vacation) {
        List<User> reviewers = new ArrayList<>();
        projectAssignmentRepository.findByUserIdAndIsActiveTrue(vacation.getUser().getId()).stream()
                .map(assignment -> assignment.getProject())
                .filter(Objects::nonNull)
                .filter(project -> Boolean.TRUE.equals(project.getIsActive())
                        && project.getStatus() == com.maxwell.chronos.enums.ProjectStatus.ACTIVE)
                .forEach(project -> {
                    User reviewer = project.getProjectManager();
                    if (reviewer != null
                            && Boolean.TRUE.equals(reviewer.getIsActive())
                            && !reviewer.getId().equals(vacation.getUser().getId())
                            && reviewers.stream().noneMatch(existing -> existing.getId().equals(reviewer.getId()))) {
                        reviewers.add(reviewer);
                    }
                });
        return reviewers;
    }

    private VacationRequestDTO toDTO(VacationRequest vacation) {
        return VacationRequestDTO.builder()
                .id(vacation.getId())
                .userId(vacation.getUser().getId())
                .userName(vacation.getUser().getFullName())
                .startDate(vacation.getStartDate())
                .endDate(vacation.getEndDate())
                .vacationType(vacation.getVacationType())
                .hours(vacation.getHours())
                .status(vacation.getStatus())
                .notes(vacation.getNotes())
                .submittedAt(vacation.getSubmittedAt())
                .approvedAt(vacation.getApprovedAt())
                .approvedByName(vacation.getApprovedBy() != null ? vacation.getApprovedBy().getFullName() : null)
                .rejectedAt(vacation.getRejectedAt())
                .rejectedByName(vacation.getRejectedBy() != null ? vacation.getRejectedBy().getFullName() : null)
                .rejectionReason(vacation.getRejectionReason())
                .createdAt(vacation.getCreatedAt())
                .updatedAt(vacation.getUpdatedAt())
                .build();
    }
}
