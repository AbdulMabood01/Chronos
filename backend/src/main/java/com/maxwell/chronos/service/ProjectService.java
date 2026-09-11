package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.domain.ProjectAssignment;
import com.maxwell.chronos.domain.ProjectHourPlan;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.ProjectAssignmentDTO;
import com.maxwell.chronos.dto.ProjectDTO;
import com.maxwell.chronos.dto.ProjectHoursDashboardDTO;
import com.maxwell.chronos.dto.ProjectHoursEmployeeDTO;
import com.maxwell.chronos.dto.SaveProjectRequest;
import com.maxwell.chronos.domain.TimeEntry;
import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.enums.ProjectStatus;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.ProjectHourPlanRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectHourPlanRepository hourPlanRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimesheetProjectSubmissionRepository projectSubmissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public List<ProjectDTO> getProjects(User requester) {
        if (requester == null || (!requester.isSuperAdmin() && !requester.isAdmin())) {
            throw new org.springframework.security.access.AccessDeniedException("Project view permission required");
        }
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDTO)
                .toList();
    }

    public List<ProjectDTO> getAssignedProjects(Long userId) {
        return getAssignedProjects(userId, null, null);
    }

    public List<ProjectDTO> getAssignedProjects(Long userId, Integer year, Integer month) {
        if ((year == null) != (month == null)) {
            throw new IllegalArgumentException("Year and month must be provided together");
        }
        if (year != null) {
            validatePeriod(year, month);
        }
        return assignmentRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(assignment -> ProjectStatus.ACTIVE.equals(assignment.getProject().getStatus()))
                .sorted(Comparator.comparing(assignment -> assignment.getProject().getCode(), String.CASE_INSENSITIVE_ORDER))
                .map(assignment -> toDTO(assignment.getProject(), List.of(toAssignmentDTO(assignment, year, month))))
                .toList();
    }

    public ProjectDTO saveProject(Long projectId, SaveProjectRequest request, User requester) {
        requireProjectAdmin(requester);
        Project project = projectId == null ? Project.builder().isActive(true).status(ProjectStatus.ACTIVE).build()
                : projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String code = clean(request.getCode());
        String name = clean(request.getName());
        if (code == null || name == null) {
            throw new IllegalArgumentException("Project code and name are required");
        }
        if (request.getProjectManagerId() == null || request.getProjectManagerHoursApproverId() == null) {
            throw new IllegalArgumentException("Project manager and PM hours approver are required");
        }
        if (request.getProjectManagerId().equals(request.getProjectManagerHoursApproverId())) {
            throw new IllegalArgumentException("Project manager cannot approve their own hours");
        }
        if (request.getTotalAllocatedHours() != null && request.getTotalAllocatedHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total allocated hours cannot be negative");
        }

        projectRepository.findByCodeIgnoreCase(code)
                .filter(existing -> project.getId() == null || !existing.getId().equals(project.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Project code already exists");
                });

        project.setCode(code.toUpperCase());
        project.setName(name);
        project.setDescription(clean(request.getDescription()));
        ProjectStatus status = request.getStatus() != null ? request.getStatus()
                : Boolean.FALSE.equals(request.getIsActive()) ? ProjectStatus.ARCHIVED : ProjectStatus.ACTIVE;
        project.setStatus(status);
        project.setIsActive(ProjectStatus.ACTIVE.equals(status));
        project.setTotalAllocatedHours(request.getTotalAllocatedHours());

        if (request.getProjectManagerId() != null) {
            User manager = userRepository.findById(request.getProjectManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager not found"));
            project.setProjectManager(manager);
        } else {
            project.setProjectManager(null);
        }

        if (request.getProjectManagerHoursApproverId() != null) {
            User approver = userRepository.findById(request.getProjectManagerHoursApproverId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager hours approver not found"));
            project.setProjectManagerHoursApprover(approver);
        } else {
            project.setProjectManagerHoursApprover(null);
        }

        Project saved = projectRepository.save(project);
        if (saved.getProjectManager() != null) {
            ProjectAssignment managerAssignment = assignmentRepository.findByProjectIdAndUserId(saved.getId(), saved.getProjectManager().getId())
                    .orElseGet(() -> ProjectAssignment.builder()
                            .project(saved)
                            .user(saved.getProjectManager())
                            .assignedBy(requester)
                            .build());
            managerAssignment.setIsActive(true);
            assignmentRepository.save(managerAssignment);
        }
        auditService.logAction(requester.getId(), projectId == null ? "PROJECT_CREATED" : "PROJECT_UPDATED",
                "Project", saved.getId(), "Code: " + saved.getCode());
        return toDTO(saved);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, User requester) {
        return assignEmployee(projectId, userId, null, null, null, requester);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal billRate, User requester) {
        requireProjectAdmin(requester);
        if (requester.getId().equals(userId)) {
            throw new IllegalArgumentException("Users cannot assign themselves to projects");
        }
        validateAssignmentDetails(startDate, endDate, billRate);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException("Inactive employees cannot be assigned to projects");
        }

        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> ProjectAssignment.builder()
                        .project(project)
                        .user(user)
                        .assignedBy(requester)
                        .build());
        assignment.setIsActive(true);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setBillRate(billRate);
        assignmentRepository.save(assignment);
        auditService.logAction(requester.getId(), "PROJECT_ASSIGNED", "Project", projectId,
                "Assigned user " + user.getFullName());
        return toDTO(project);
    }

    public ProjectDTO updateAssignmentDates(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                            BigDecimal billRate, User requester) {
        requireProjectAdmin(requester);
        validateAssignmentDetails(startDate, endDate, billRate);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setBillRate(billRate);
        assignmentRepository.save(assignment);
        auditService.logAction(requester.getId(), "PROJECT_ASSIGNMENT_UPDATED", "Project", projectId,
                "Updated assignment dates for " + assignment.getUser().getFullName());
        return toDTO(assignment.getProject());
    }

    public ProjectDTO updatePlannedHours(Long projectId, Long userId, BigDecimal plannedHours, User requester) {
        if (plannedHours != null && plannedHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Planned hours cannot be negative");
        }
        requireCanPlanProject(projectId, requester);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        assignment.setPlannedHours(plannedHours);
        assignmentRepository.save(assignment);
        auditService.logAction(requester.getId(), "PROJECT_UPDATED", "Project", projectId,
                "Updated planned hours for " + assignment.getUser().getFullName());
        return toDTO(assignment.getProject());
    }

    public ProjectDTO updatePlannedHours(Long projectId, Long userId, Integer year, Integer month, BigDecimal plannedHours, User requester) {
        validatePeriod(year, month);
        if (plannedHours != null && plannedHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Planned hours cannot be negative");
        }
        requireCanPlanProject(projectId, requester);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ProjectHourPlan plan = hourPlanRepository.findByProjectIdAndUserIdAndYearAndMonth(projectId, userId, year, month)
                .orElseGet(() -> ProjectHourPlan.builder()
                        .project(project)
                        .user(targetUser)
                        .year(year)
                        .month(month)
                        .build());
        plan.setPlannedHours(plannedHours != null ? plannedHours : BigDecimal.ZERO);
        plan.setUpdatedBy(requester);
        hourPlanRepository.save(plan);
        auditService.logAction(requester.getId(), "PROJECT_UPDATED", "Project", projectId,
                "Updated planned hours for " + targetUser.getFullName() + " in " + year + "-" + month);
        return toDTO(project);
    }

    public List<ProjectHoursDashboardDTO> getProjectHoursDashboard(int year, int month, User requester) {
        validatePeriod(year, month);
        if (requester == null || (!requester.isSuperAdmin() && !requester.isAdmin() && !canReviewProjects(requester.getId()))) {
            throw new IllegalArgumentException("Project dashboard permission required");
        }

        List<Project> projects = !requester.isAdmin() && !requester.isSuperAdmin()
                ? projectRepository.findByProjectManagerIdAndStatus(requester.getId(), ProjectStatus.ACTIVE)
                : projectRepository.findAll();
        List<Timesheet> timesheets = timesheetRepository.findByYearAndMonth(year, month);
        Map<Long, Timesheet> timesheetsByUser = timesheets.stream()
                .collect(Collectors.toMap(timesheet -> timesheet.getUser().getId(), Function.identity(), (left, right) -> left));

        return projects.stream()
                .sorted(Comparator.comparing(Project::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(project -> toProjectHoursDTO(project, year, month, timesheetsByUser))
                .toList();
    }

    public ProjectDTO removeEmployee(Long projectId, Long userId, User requester) {
        requireProjectAdmin(requester);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        assignment.setIsActive(false);
        if (assignment.getEndDate() == null) {
            assignment.setEndDate(LocalDate.now());
        }
        assignmentRepository.save(assignment);
        auditService.logAction(requester.getId(), "PROJECT_UNASSIGNED", "Project", projectId,
                "Ended assignment for " + assignment.getUser().getFullName());
        return toDTO(assignment.getProject());
    }

    public boolean isAssigned(Long projectId, Long userId) {
        return assignmentRepository.existsByProjectIdAndUserIdAndIsActiveTrue(projectId, userId);
    }

    public boolean managesProject(Long projectId, Long managerId) {
        return projectRepository.findById(projectId)
                .map(project -> project.getProjectManager() != null && project.getProjectManager().getId().equals(managerId))
                .orElse(false);
    }

    public boolean canReviewProjects(Long userId) {
        return projectRepository.existsByProjectManagerIdOrProjectManagerHoursApproverId(userId, userId);
    }

    public boolean canApproveProjectManagerHours(Long projectId, Long approverId) {
        return projectRepository.findById(projectId)
                .map(project -> project.getProjectManagerHoursApprover() != null
                        && project.getProjectManagerHoursApprover().getId().equals(approverId))
                .orElse(false);
    }

    private void requireProjectAdmin(User user) {
        if (user == null || !user.canManageProjects()) {
            throw new org.springframework.security.access.AccessDeniedException("Admin permission required");
        }
    }

    private void requireCanPlanProject(Long projectId, User requester) {
        if (requester == null || requester.isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Project planning permission required");
        }
        if (requester.isAdmin()) {
            return;
        }
        if (managesProject(projectId, requester.getId())) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException("Project planning permission required");
    }

    private void validatePeriod(Integer year, Integer month) {
        if (year == null || month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("Valid year and month are required");
        }
        YearMonth.of(year, month);
    }

    private void validateAssignmentDetails(LocalDate startDate, LocalDate endDate, BigDecimal billRate) {
        if (startDate == null || endDate == null || billRate == null) {
            throw new IllegalArgumentException("Assignment start date, end date, and bill rate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Project assignment start date cannot be after end date");
        }
        if (billRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bill rate cannot be negative");
        }
    }

    private ProjectDTO toDTO(Project project) {
        List<ProjectAssignmentDTO> assignments = assignmentRepository.findByProjectId(project.getId()).stream()
                .sorted(Comparator.comparing((ProjectAssignment assignment) -> !Boolean.TRUE.equals(assignment.getIsActive()))
                        .thenComparing(assignment -> assignment.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toAssignmentDTO)
                .toList();
        return toDTO(project, assignments);
    }

    private ProjectDTO toDTO(Project project, List<ProjectAssignmentDTO> assignments) {
        return ProjectDTO.builder()
                .id(project.getId())
                .code(project.getCode())
                .name(project.getName())
                .description(project.getDescription())
                .isActive(project.getIsActive())
                .status(project.getStatus())
                .totalAllocatedHours(project.getTotalAllocatedHours())
                .projectManagerId(project.getProjectManager() != null ? project.getProjectManager().getId() : null)
                .projectManagerName(project.getProjectManager() != null ? project.getProjectManager().getFullName() : null)
                .projectManagerHoursApproverId(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getId() : null)
                .projectManagerHoursApproverName(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getFullName() : null)
                .assignments(assignments)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ProjectAssignmentDTO toAssignmentDTO(ProjectAssignment assignment) {
        return toAssignmentDTO(assignment, null, null);
    }

    private ProjectAssignmentDTO toAssignmentDTO(ProjectAssignment assignment, Integer year, Integer month) {
        User user = assignment.getUser();
        BigDecimal plannedHours = assignment.getPlannedHours();
        if (year != null && month != null) {
            plannedHours = hourPlanRepository
                    .findByProjectIdAndUserIdAndYearAndMonth(assignment.getProject().getId(), user.getId(), year, month)
                    .map(plan -> plan.getPlannedHours() != null ? plan.getPlannedHours() : BigDecimal.ZERO)
                    .orElse(plannedHours);
        }
        return ProjectAssignmentDTO.builder()
                .id(assignment.getId())
                .userId(user.getId())
                .employeeId(user.getEmployeeId())
                .userName(user.getFullName())
                .email(user.getEmail())
                .jobTitle(user.getJobTitle())
                .isActive(assignment.getIsActive())
                .plannedHours(plannedHours)
                .billRate(assignment.getBillRate())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private ProjectHoursDashboardDTO toProjectHoursDTO(Project project, int year, int month, Map<Long, Timesheet> timesheetsByUser) {
        List<ProjectAssignment> assignments = assignmentRepository.findByProjectId(project.getId());
        Map<Long, ProjectAssignment> assignmentsByUser = assignments.stream()
                .collect(Collectors.toMap(assignment -> assignment.getUser().getId(), Function.identity(), (left, right) -> left));
        Map<Long, ProjectHourPlan> plansByUser = hourPlanRepository.findByProjectIdAndYearAndMonth(project.getId(), year, month).stream()
                .collect(Collectors.toMap(plan -> plan.getUser().getId(), Function.identity(), (left, right) -> left));

        Map<Long, ProjectHoursEmployeeDTO> rowsByUser = new HashMap<>();
        Set<Long> userIds = new HashSet<>(assignmentsByUser.keySet());
        timesheetsByUser.values().stream()
                .filter(Objects::nonNull)
                .filter(timesheet -> timesheet.getTimeEntries().stream().anyMatch(entry -> isEntryForProject(entry, project)))
                .forEach(timesheet -> userIds.add(timesheet.getUser().getId()));

        for (Long userId : userIds) {
            ProjectAssignment assignment = assignmentsByUser.get(userId);
            Timesheet timesheet = timesheetsByUser.get(userId);
            User employee = assignment != null ? assignment.getUser() : timesheet != null ? timesheet.getUser() : null;
            if (employee != null) {
                rowsByUser.put(userId, toProjectHoursEmployeeDTO(project, assignment, employee, timesheet, plansByUser.get(userId)));
            }
        }

        List<ProjectHoursEmployeeDTO> employees = new ArrayList<>(rowsByUser.values());
        employees.sort(Comparator.comparing(ProjectHoursEmployeeDTO::getUserName, String.CASE_INSENSITIVE_ORDER));

        return ProjectHoursDashboardDTO.builder()
                .projectId(project.getId())
                .projectCode(project.getCode())
                .projectName(project.getName())
                .projectManagerName(project.getProjectManager() != null ? project.getProjectManager().getFullName() : null)
                .plannedHours(sum(employees, ProjectHoursEmployeeDTO::getPlannedHours))
                .draftHours(sum(employees, ProjectHoursEmployeeDTO::getDraftHours))
                .submittedHours(sum(employees, ProjectHoursEmployeeDTO::getSubmittedHours))
                .approvedHours(sum(employees, ProjectHoursEmployeeDTO::getApprovedHours))
                .rejectedHours(sum(employees, ProjectHoursEmployeeDTO::getRejectedHours))
                .totalLoggedHours(sum(employees, ProjectHoursEmployeeDTO::getTotalLoggedHours))
                .employees(employees)
                .build();
    }

    private ProjectHoursEmployeeDTO toProjectHoursEmployeeDTO(Project project, ProjectAssignment assignment, User user,
                                                              Timesheet timesheet, ProjectHourPlan plan) {
        BigDecimal logged = BigDecimal.ZERO;
        TimesheetProjectSubmission submission = null;
        if (timesheet != null) {
            logged = timesheet.getTimeEntries().stream()
                    .filter(entry -> isEntryForProject(entry, project))
                    .map(entry -> entry.getHours() != null ? entry.getHours() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            submission = projectSubmissionRepository.findByTimesheetIdAndProjectId(timesheet.getId(), project.getId()).orElse(null);
        }

        TimesheetStatus status = submission != null ? submission.getStatus() : TimesheetStatus.DRAFT;
        return ProjectHoursEmployeeDTO.builder()
                .userId(user.getId())
                .employeeId(user.getEmployeeId())
                .userName(user.getFullName())
                .email(user.getEmail())
                .jobTitle(user.getJobTitle())
                .plannedHours(plannedHoursFor(assignment, plan))
                .draftHours(TimesheetStatus.DRAFT.equals(status) ? logged : BigDecimal.ZERO)
                .submittedHours(TimesheetStatus.SUBMITTED.equals(status) || TimesheetStatus.CHANGE_REQUESTED.equals(status) ? logged : BigDecimal.ZERO)
                .approvedHours(TimesheetStatus.APPROVED.equals(status) || TimesheetStatus.LOCKED.equals(status) ? logged : BigDecimal.ZERO)
                .rejectedHours(TimesheetStatus.REJECTED.equals(status) ? logged : BigDecimal.ZERO)
                .totalLoggedHours(logged)
                .status(status)
                .assignmentActive(assignment != null && Boolean.TRUE.equals(assignment.getIsActive()))
                .assignmentStartDate(assignment != null ? assignment.getStartDate() : null)
                .assignmentEndDate(assignment != null ? assignment.getEndDate() : null)
                .timesheetId(timesheet != null ? timesheet.getId() : null)
                .submissionId(submission != null ? submission.getId() : null)
                .build();
    }

    private BigDecimal plannedHoursFor(ProjectAssignment assignment, ProjectHourPlan plan) {
        if (plan != null && plan.getPlannedHours() != null) {
            return plan.getPlannedHours();
        }
        if (assignment != null && assignment.getPlannedHours() != null) {
            return assignment.getPlannedHours();
        }
        return BigDecimal.ZERO;
    }

    private boolean isEntryForProject(TimeEntry entry, Project project) {
        return entry.getProject() != null && entry.getProject().getId().equals(project.getId());
    }

    private BigDecimal sum(List<ProjectHoursEmployeeDTO> employees, Function<ProjectHoursEmployeeDTO, BigDecimal> extractor) {
        return employees.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
