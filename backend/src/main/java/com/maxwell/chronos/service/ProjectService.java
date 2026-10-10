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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    private final CompanyAccessService access;

    public List<ProjectDTO> getProjects(User requester) {
        if (requester == null || !(access.hasPlatformRole(requester.getId(), "PLATFORM_ADMIN")
                || access.hasAnyCompanyRole(requester.getId(), "COMPANY_ADMIN")
                || canManageProjects(requester.getId()) || canReviewProjects(requester.getId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Project view permission required");
        }
        return visibleProjects(requester).stream()
                .filter(project -> canViewManagementProject(project, requester.getId()))
                .sorted(Comparator.comparing(Project::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(project -> {
                    ProjectDTO dto = toDTO(project);
                    dto.setCanManage(access.mayManageProject(project.getId(), requester.getId()));
                    return dto;
                })
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
        return assignmentRepository.findByUserId(userId).stream()
                .filter(assignment -> access.maySubmit(assignment.getProject().getId(), userId))
                .sorted(Comparator.comparing(assignment -> assignment.getProject().getCode(), String.CASE_INSENSITIVE_ORDER))
                .map(assignment -> toDTO(assignment.getProject(), List.of(toAssignmentDTO(assignment, year, month))))
                .toList();
    }

    public ProjectDTO saveProject(Long projectId, SaveProjectRequest request, User requester) {
        if (requester == null) throw new org.springframework.security.access.AccessDeniedException("Sign in required");
        if (access.hasPlatformRole(requester.getId(), "PLATFORM_ADMIN"))
            throw new org.springframework.security.access.AccessDeniedException("Platform Admin cannot edit projects");
        Project project = projectId == null ? Project.builder().isActive(false).status(ProjectStatus.DRAFT).build()
                : projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (projectId == null) {
            Long companyId = request.getCompanyId();
            if (companyId == null) throw new IllegalArgumentException("Company is required");
            if (!access.mayCreateProject(companyId, requester.getId()))
                throw new org.springframework.security.access.AccessDeniedException("Company Admin or company Project Admin role required");
            jdbc.queryForObject("SELECT id FROM companies WHERE id=? FOR UPDATE", Long.class, companyId);
            if(!access.mayCreateProject(companyId,requester.getId()))throw new org.springframework.security.access.AccessDeniedException("Company project creation permission required");
            access.requireProjectCapacity(companyId);
            project.setCompanyId(companyId);
            long owner=request.getOwnerUserId()==null?requester.getId():request.getOwnerUserId();
            if(owner!=requester.getId()&&!access.hasCompanyRole(companyId,requester.getId(),"COMPANY_ADMIN"))throw new org.springframework.security.access.AccessDeniedException("Company Admin permission required to appoint another project owner");
            if(request.getOwnerUserId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=? AND m.user_id=? AND m.status='ACTIVE' AND u.is_active=TRUE AND u.admin_locked=FALSE AND u.password_hash IS NOT NULL)",Boolean.class,companyId,owner))||access.hasPlatformRole(owner,"PLATFORM_ADMIN"))throw new IllegalArgumentException("Choose an active company member as project owner");
            project.setOwnerUserId(owner);
        } else {
            access.lockCompanyAdministration(project.getCompanyId());
            requireProjectAdmin(projectId, requester);
            if (request.getCompanyId() != null && !request.getCompanyId().equals(project.getCompanyId()))
                throw new IllegalArgumentException("Project company cannot be changed");
        }
        ProjectStatus previousStatus = project.getStatus();
        if(request.getStatus()==ProjectStatus.ARCHIVED||request.getStatus()==ProjectStatus.COMPLETED)requireFinalizedExpenses(projectId);

        Long previousManagerId = project.getProjectManager() == null ? null : project.getProjectManager().getId();
        Long previousApproverId = project.getProjectManagerHoursApprover() == null ? null : project.getProjectManagerHoursApprover().getId();
        String code = clean(request.getCode());
        String name = clean(request.getName());
        if (code == null || name == null) {
            throw new IllegalArgumentException("Project code and name are required");
        }
        if (request.getTotalAllocatedHours() != null && request.getTotalAllocatedHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total allocated hours cannot be negative");
        }
        if (request.getExpenseBudget() != null && (request.getExpenseBudget().signum() < 0 || request.getExpenseBudget().scale() > 2)) {
            throw new IllegalArgumentException("Expense budget must be a nonnegative monetary amount with at most two decimals");
        }

        projectRepository.findByCompanyIdAndCodeIgnoreCase(project.getCompanyId(), code)
                .filter(existing -> project.getId() == null || !existing.getId().equals(project.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Project code already exists");
                });

        project.setCode(code.toUpperCase());
        project.setName(name);
        project.setDescription(clean(request.getDescription()));
        ProjectStatus status = request.getStatus() != null ? request.getStatus()
                : projectId == null ? ProjectStatus.DRAFT : project.getStatus();
        if (projectId == null && status != ProjectStatus.DRAFT) {
            throw new IllegalArgumentException("Create the project as a draft before activating it");
        }
        if (status != ProjectStatus.DRAFT && (request.getProjectManagerId() == null || request.getProjectManagerHoursApproverId() == null)) {
            throw new IllegalArgumentException("Project manager and PM hours approver are required");
        }
        if (previousStatus == ProjectStatus.DRAFT && status != ProjectStatus.DRAFT && status != ProjectStatus.ACTIVE) {
            throw new IllegalArgumentException("Activate the draft before changing it to another status");
        }
        if (status == ProjectStatus.ACTIVE && previousStatus == ProjectStatus.DRAFT) {
            if (request.getProjectManagerId() == null || request.getProjectManagerHoursApproverId() == null) {
                throw new IllegalArgumentException("Choose a project manager and PM hours approver before activation");
            }
            if (assignmentRepository.findByProjectIdAndIsActiveTrue(projectId).isEmpty()) {
                throw new IllegalArgumentException("Assign at least one active team member before activation");
            }
        }
        if (projectId != null && (status == ProjectStatus.COMPLETED || status == ProjectStatus.ARCHIVED)
                && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM timesheet_approval_periods WHERE project_id=? AND status='SUBMITTED') OR EXISTS (SELECT 1 FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE e.project_id=? AND e.hours>0 AND NOT EXISTS (SELECT 1 FROM timesheet_approval_periods a WHERE a.project_id=e.project_id AND a.user_id=t.user_id AND e.entry_date BETWEEN a.period_start AND a.period_end AND a.status='APPROVED'))", Boolean.class, projectId, projectId))) {
            throw new IllegalArgumentException("This project has unfinalized hours. Submit draft hours, correct and resubmit rejected hours, and approve pending timesheets before completing or archiving. Remove logged hours only if they were entered in error.");
        }
        if(projectId!=null && previousStatus!=null && java.util.Set.of(ProjectStatus.COMPLETED,ProjectStatus.ARCHIVED).contains(previousStatus)
                && !java.util.Set.of(ProjectStatus.COMPLETED,ProjectStatus.ARCHIVED).contains(status))access.requireProjectCapacity(project.getCompanyId());
        project.setStatus(status);
        project.setIsActive(ProjectStatus.ACTIVE.equals(status));
        project.setTotalAllocatedHours(request.getTotalAllocatedHours());
        BigDecimal previousBudget = project.getExpenseBudget();
        project.setExpenseBudget(request.getExpenseBudget());
        if (request.getApprovalFrequency() != null) {
            String frequency = request.getApprovalFrequency().trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("DAILY", "WEEKLY", "MONTHLY").contains(frequency))
                throw new IllegalArgumentException("Approval frequency must be daily, weekly, or monthly");
            if (projectId == null) {
                project.setApprovalFrequency(frequency);
            } else if (!frequency.equals(project.getPendingApprovalFrequency())
                    && !frequency.equals(activeApprovalFrequency(project))) {
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate next = switch (activeApprovalFrequency(project)) {
                    case "DAILY" -> today.plusDays(1);
                    case "WEEKLY" -> today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
                    default -> today.withDayOfMonth(1).plusMonths(1);
                };
                project.setPendingApprovalFrequency(frequency);
                project.setApprovalFrequencyEffectiveOn(next);
                jdbc.update("DELETE FROM project_approval_frequency_changes WHERE project_id=? AND effective_on>?", projectId, today);
                jdbc.update("INSERT INTO project_approval_frequency_changes(project_id,effective_on,frequency) VALUES (?,?,?) ON CONFLICT (project_id,effective_on) DO UPDATE SET frequency=excluded.frequency", projectId, next, frequency);
            } else if (frequency.equals(activeApprovalFrequency(project)) && project.getApprovalFrequencyEffectiveOn() != null
                    && project.getApprovalFrequencyEffectiveOn().isAfter(java.time.LocalDate.now())) {
                jdbc.update("DELETE FROM project_approval_frequency_changes WHERE project_id=? AND effective_on=?", projectId, project.getApprovalFrequencyEffectiveOn());
                project.setPendingApprovalFrequency(null);
                project.setApprovalFrequencyEffectiveOn(null);
            }
        }

        if (request.getProjectManagerId() != null) {
            User manager = userRepository.findById(request.getProjectManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager not found"));
            requireEligibleReviewer(manager);
            if (!access.companyIds(manager.getId()).contains(project.getCompanyId()))
                throw new IllegalArgumentException("Project Manager must accept a company invitation first");
            project.setProjectManager(manager);
        } else {
            project.setProjectManager(null);
        }

        if (request.getProjectManagerHoursApproverId() != null) {
            User approver = userRepository.findById(request.getProjectManagerHoursApproverId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager hours approver not found"));
            requireEligibleReviewer(approver);
            if (!access.hasCompanyRole(project.getCompanyId(), approver.getId(), "PROJECT_ADMIN")
                    && (project.getId() == null || !access.hasProjectRole(project.getId(), approver.getId(), "PROJECT_ADMIN")))
                throw new IllegalArgumentException("PM hours approver must be an appointed Project Admin");
            if (Objects.equals(request.getProjectManagerId(), approver.getId()))
                throw new IllegalArgumentException("Project Manager cannot approve their own hours");
            project.setProjectManagerHoursApprover(approver);
        } else {
            project.setProjectManagerHoursApprover(null);
        }

        Project saved = projectRepository.save(project);
        if (projectId == null) access.activateProjectRole(saved.getCompanyId(), saved.getId(), saved.getOwnerUserId(), "PROJECT_ADMIN", requester.getId());
        if (request.getProjectManagerId() != null) access.activateProjectRole(saved.getCompanyId(), saved.getId(), request.getProjectManagerId(), "PROJECT_MANAGER", requester.getId());
        if (request.getProjectManagerHoursApproverId() != null && !access.hasProjectRole(saved.getId(), request.getProjectManagerHoursApproverId(), "PROJECT_ADMIN"))
        {
            if(!Objects.equals(saved.getOwnerUserId(),request.getProjectManagerHoursApproverId())&&!access.hasCompanyRole(saved.getCompanyId(),requester.getId(),"COMPANY_ADMIN"))
                throw new org.springframework.security.access.AccessDeniedException("A Company Admin must appoint this project's hours approver as Project Admin first");
            access.activateProjectRole(saved.getCompanyId(), saved.getId(), request.getProjectManagerHoursApproverId(), "PROJECT_ADMIN", requester.getId());
        }
        if (!Objects.equals(previousBudget, saved.getExpenseBudget())) {
            jdbc.update("INSERT INTO project_expense_budget_history(project_id,actor_id,previous_budget,new_budget) VALUES (?,?,?,?)",
                    saved.getId(), requester.getId(), previousBudget, saved.getExpenseBudget());
        }
        if (projectId != null && status != ProjectStatus.DRAFT && (!Objects.equals(previousManagerId, request.getProjectManagerId())
                || !Objects.equals(previousApproverId, request.getProjectManagerHoursApproverId()))) {
            transferPendingApprovals(saved, requester, previousManagerId, previousApproverId);
        }
        auditService.logAction(requester.getId(), projectId == null ? "PROJECT_CREATED" : "PROJECT_UPDATED",
                "Project", saved.getId(), "Code: " + saved.getCode());
        ProjectDTO dto = toDTO(saved);
        dto.setCanManage(access.mayManageProject(saved.getId(), requester.getId()));
        return dto;
    }

    private void requireFinalizedExpenses(Long projectId){
        if(projectId!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project_expenses WHERE project_id=? AND status='PENDING_APPROVAL') OR EXISTS(SELECT 1 FROM timesheet_approval_periods WHERE project_id=? AND opening_status='APPROVED' AND status IN ('APPROVED','DRAFT','REJECTED'))",Boolean.class,projectId,projectId)))
            throw new IllegalArgumentException("Review pending expenses and finalize timesheet corrections before archiving or completing this project");
    }
    public ProjectDTO archiveProject(long projectId,User requester){
        long company=access.companyId(projectId);access.requireActiveCompanyAccess(company,requester.getId());access.lockCompanyAdministration(company);
        if(!access.hasCompanyRole(company,requester.getId(),"COMPANY_ADMIN")&&!access.mayManageProject(projectId,requester.getId()))throw new org.springframework.security.access.AccessDeniedException("Company or Project Admin permission required");
        requireFinalizedExpenses(projectId);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM timesheet_approval_periods WHERE project_id=? AND status='SUBMITTED') OR EXISTS(SELECT 1 FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE e.project_id=? AND e.hours>0 AND NOT EXISTS(SELECT 1 FROM timesheet_approval_periods a WHERE a.project_id=e.project_id AND a.user_id=t.user_id AND e.entry_date BETWEEN a.period_start AND a.period_end AND a.status='APPROVED'))",Boolean.class,projectId,projectId)))throw new IllegalArgumentException("Finalize project hours before archiving");
        Project project=projectRepository.findById(projectId).orElseThrow();project.setStatus(ProjectStatus.ARCHIVED);project.setIsActive(false);projectRepository.save(project);
        auditService.logRequiredAction(requester.getId(),com.maxwell.chronos.enums.AuditAction.PROJECT_UPDATED,"Project",projectId,"Archived project in company "+company);return toDTO(project);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, User requester) {
        return assignEmployee(projectId, userId, null, null, null, requester);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal billRate, User requester) {
        return assignEmployee(projectId, userId, startDate, endDate, billRate, null, requester);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal billRate, BigDecimal plannedHours, User requester) {
        return assignEmployee(projectId,userId,startDate,endDate,billRate,plannedHours,false,false,requester);
    }

    public ProjectDTO assignEmployee(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal billRate, BigDecimal plannedHours, boolean approveHours, boolean approveExpenses, User requester) {
        access.lockCompanyAdministration(access.companyId(projectId));
        requireProjectAdmin(projectId, requester);
        if (plannedHours == null || plannedHours.signum() < 0 || (plannedHours.signum()==0 && !approveHours && !approveExpenses)) {
            throw new IllegalArgumentException("Assigned hours must be greater than zero");
        }
        if (requester.getId().equals(userId)) {
            throw new IllegalArgumentException("Users cannot assign themselves to projects");
        }
        validateAssignmentDetails(startDate, endDate, billRate);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!access.companyIds(userId).contains(project.getCompanyId()))
            throw new IllegalArgumentException("Team member must accept a company invitation first");
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException("Inactive employees cannot be assigned to projects");
        }

        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> ProjectAssignment.builder()
                        .project(project)
                        .user(user)
                        .assignedBy(requester)
                        .build());
        assignment.setPlannedHours(plannedHours);
        assignment.setIsActive(true);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setBillRate(billRate);
        assignmentRepository.save(assignment);
        access.activateProjectRole(project.getCompanyId(), projectId, userId, "USER", requester.getId());
        jdbc.update("UPDATE moderator_grants SET revoked_at=now() WHERE project_id=? AND moderator_user_id=? AND revoked_at IS NULL",projectId,userId);
        if(approveHours||approveExpenses)jdbc.update("INSERT INTO moderator_grants(company_id,project_id,moderator_user_id,timesheets,expenses,starts_on,ends_on,granted_by_user_id) VALUES (?,?,?,?,?,?,?,?)",project.getCompanyId(),projectId,userId,approveHours,approveExpenses,startDate,endDate,requester.getId());
        auditService.logAction(requester.getId(), "PROJECT_APPROVAL_ACCESS_UPDATED", "Project", projectId,
                "User " + userId + "; hours=" + approveHours + "; expenses=" + approveExpenses);
        auditService.logAction(requester.getId(), "PROJECT_ASSIGNED", "Project", projectId,
                "Assigned user " + user.getFullName());
        return toDTO(project);
    }

    public ProjectDTO updateAssignmentDates(Long projectId, Long userId, LocalDate startDate, LocalDate endDate,
                                     BigDecimal billRate, User requester) {
        access.lockCompanyAdministration(access.companyId(projectId));
        requireProjectAdmin(projectId, requester);
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
        access.lockCompanyAdministration(access.companyId(projectId));
        if (plannedHours != null && plannedHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Planned hours cannot be negative");
        }
        requireCanPlanProject(projectId, requester);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        requireActiveAssignment(assignment);
        assignment.setPlannedHours(plannedHours);
        assignmentRepository.save(assignment);
        auditService.logAction(requester.getId(), "PROJECT_UPDATED", "Project", projectId,
                "Updated planned hours for " + assignment.getUser().getFullName());
        return toDTO(assignment.getProject());
    }

    public ProjectDTO updatePlannedHours(Long projectId, Long userId, Integer year, Integer month, BigDecimal plannedHours, User requester) {
        access.lockCompanyAdministration(access.companyId(projectId));
        validatePeriod(year, month);
        if (plannedHours != null && plannedHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Planned hours cannot be negative");
        }
        requireCanPlanProject(projectId, requester);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        requireActiveAssignment(assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found")));
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
        if (requester == null || !(access.hasPlatformRole(requester.getId(), "PLATFORM_ADMIN")
                || access.hasAnyCompanyRole(requester.getId(), "COMPANY_ADMIN")
                || canManageProjects(requester.getId()) || canReviewProjects(requester.getId()))) {
            throw new IllegalArgumentException("Project dashboard permission required");
        }

        List<Project> projects = visibleProjects(requester).stream()
                .filter(project -> canViewManagementProject(project, requester.getId())).toList();
        List<Timesheet> timesheets = timesheetRepository.findByYearAndMonth(year, month);
        return projects.stream()
                .sorted(Comparator.comparing(Project::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(project -> toProjectHoursDTO(project, year, month, timesheets.stream()
                        .filter(sheet -> project.getCompanyId().equals(sheet.getCompanyId()))
                        .collect(Collectors.toMap(sheet -> sheet.getUser().getId(), Function.identity()))))
                .toList();
    }

    private void requireActiveAssignment(ProjectAssignment assignment) {
        if (!Boolean.TRUE.equals(assignment.getIsActive())) {
            throw new IllegalArgumentException("Hours for offboarded members are frozen");
        }
    }

    private boolean pendingApproval(TimesheetProjectSubmission submission) {
        return submission.getStatus() == TimesheetStatus.SUBMITTED || submission.getStatus() == TimesheetStatus.CHANGE_REQUESTED;
    }

    private BigDecimal approvedHoursToDate(Long projectId, Long userId) {
        return jdbc.queryForObject("SELECT COALESCE(sum(e.hours),0) FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id JOIN timesheet_approval_periods a ON a.user_id=t.user_id AND a.project_id=e.project_id AND e.entry_date BETWEEN a.period_start AND a.period_end WHERE e.project_id=? AND t.user_id=? AND a.status='APPROVED'", BigDecimal.class, projectId, userId);
    }

    public ProjectDTO removeEmployee(Long projectId, Long userId, User requester) {
        return removeEmployee(projectId, userId, null, requester);
    }

    public ProjectDTO removeEmployee(Long projectId, Long userId, Long replacementManagerId, User requester) {
        access.lockCompanyAdministration(access.companyId(projectId));
        requireProjectAdmin(projectId, requester);
        ProjectAssignment assignment = assignmentRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        requireActiveAssignment(assignment);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM timesheet_approval_periods WHERE project_id=? AND user_id=? AND status='SUBMITTED')", Boolean.class, projectId, userId))) {
            throw new IllegalArgumentException("Please approve or reject pending hours before offboarding this employee");
        }
        Project project = assignment.getProject();
        if (project.getProjectManager() != null && project.getProjectManager().getId().equals(userId)) {
            if (replacementManagerId == null || replacementManagerId.equals(userId)) {
                throw new IllegalArgumentException("Assign a secondary PM before offboarding the project manager");
            }
            User replacementUser = userRepository.findById(replacementManagerId)
                    .orElseThrow(() -> new IllegalArgumentException("Replacement PM not found"));
            if (!access.mayManageProject(projectId, replacementManagerId)) {
                ProjectAssignment replacement = assignmentRepository.findByProjectIdAndUserId(projectId, replacementManagerId)
                        .orElseThrow(() -> new IllegalArgumentException("The secondary PM must be an active project team member or Project Admin"));
                requireActiveAssignment(replacement);
            }
            requireEligibleReviewer(replacementUser);
            if (!access.companyIds(replacementManagerId).contains(project.getCompanyId()))
                throw new IllegalArgumentException("Replacement PM must belong to the project company");
            Long previousApproverId = project.getProjectManagerHoursApprover() == null ? null : project.getProjectManagerHoursApprover().getId();
            project.setProjectManager(replacementUser);
            projectRepository.save(project);
            access.activateProjectRole(project.getCompanyId(), projectId, replacementManagerId, "PROJECT_MANAGER", requester.getId());
            transferPendingApprovals(project, requester, userId, previousApproverId);
        }
        assignment.setPlannedHours(approvedHoursToDate(projectId, userId));
        assignment.setIsActive(false);
        if (assignment.getEndDate() == null) {
            assignment.setEndDate(LocalDate.now());
        }
        assignmentRepository.save(assignment);
        access.removeProjectRole(projectId, userId, "USER");
        auditService.logAction(requester.getId(), "PROJECT_UNASSIGNED", "Project", projectId,
                "Ended assignment for " + assignment.getUser().getFullName());
        return toDTO(assignment.getProject());
    }

    private void requireEligibleReviewer(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive()) || access.hasPlatformRole(user.getId(),"PLATFORM_ADMIN")) {
            throw new IllegalArgumentException("Choose an active employee or Project Admin as project manager or PM hours approver");
        }
    }

    private void transferPendingApprovals(Project project, User requester, Long previousManagerId, Long previousApproverId) {
        for (var submission : projectSubmissionRepository.findByProjectId(project.getId())) {
            if (!pendingApproval(submission)) continue;
            User next = project.getProjectManager().getId().equals(submission.getTimesheet().getUser().getId())
                    ? project.getProjectManagerHoursApprover() : project.getProjectManager();
            if (next == null) throw new IllegalArgumentException("Select a PM hours approver before transferring pending approvals");
            Long previous = submission.getAssignedApprover() != null ? submission.getAssignedApprover().getId()
                    : Objects.equals(previousManagerId, submission.getTimesheet().getUser().getId()) ? previousApproverId : previousManagerId;
            submission.setAssignedApprover(next);
            if (Objects.equals(previous, next.getId())) continue;
            projectSubmissionRepository.save(submission);
            auditService.logAction(requester.getId(), "PROJECT_MANAGER_CHANGED", "TimesheetProjectSubmission", submission.getId(),
                    "Approval transferred from user " + previous + " to user " + next.getId() + " because project ownership or PM hours approver changed");
            notificationService.markApprovalReassigned(submission.getId());
            notificationService.createNotification(next.getId(), "TIMESHEET_SUBMITTED", "Approval transferred to you",
                    "A pending approval for " + project.getCode() + " was transferred to you during a project handover.",
                    submission.getId(), "TimesheetProjectSubmission");
        }
        auditService.logAction(requester.getId(), "PROJECT_MANAGER_CHANGED", "Project", project.getId(),
                "PM: " + previousManagerId + " -> " + project.getProjectManager().getId()
                        + "; PM hours approver: " + previousApproverId + " -> " + (project.getProjectManagerHoursApprover() == null ? null : project.getProjectManagerHoursApprover().getId()));
    }

    public boolean isAssigned(Long projectId, Long userId) {
        return assignmentRepository.existsByProjectIdAndUserIdAndIsActiveTrue(projectId, userId);
    }

    public boolean managesProject(Long projectId, Long managerId) {
        return projectRepository.findById(projectId)
                .map(project -> project.getProjectManager() != null && project.getProjectManager().getId().equals(managerId))
                .orElse(false);
    }

    public Set<Long> visibleProjectIds(User requester) {
        if (requester == null) return Set.of();
        return visibleProjects(requester).stream().map(Project::getId).collect(Collectors.toSet());
    }

    public Set<Long> visibleEmployeeIds(User requester) {
        var projectIds = visibleProjectIds(requester);
        return assignmentRepository.findAll().stream()
                .filter(a -> projectIds.contains(a.getProject().getId()))
                .map(a -> a.getUser().getId()).collect(Collectors.toSet());
    }

    private List<Project> visibleProjects(User requester) {
        if (access.hasPlatformRole(requester.getId(), "PLATFORM_ADMIN")) return List.of();
        return projectRepository.findAll().stream().filter(p ->
                access.companyIds(requester.getId()).contains(p.getCompanyId()) &&
                (access.hasCompanyRole(p.getCompanyId(), requester.getId(), "COMPANY_ADMIN") ||
                 access.hasCompanyRole(p.getCompanyId(),requester.getId(),"PROJECT_ADMIN") ||
                 access.hasProjectRole(p.getId(), requester.getId(), "PROJECT_ADMIN") ||
                 access.hasProjectRole(p.getId(), requester.getId(), "PROJECT_MANAGER") ||
                 access.hasProjectRole(p.getId(), requester.getId(), "USER") ||
                 access.hasModeratorGrant(p.getId(), requester.getId(), false) ||
                 access.hasModeratorGrant(p.getId(), requester.getId(), true))).toList();
    }

    public boolean canReviewProjects(Long userId) {
        return access.hasAnyProjectRole(userId, "PROJECT_MANAGER") || access.hasAnyProjectRole(userId, "PROJECT_ADMIN")
                || access.hasAnyModeratorGrant(userId);
    }

    public boolean canViewProjects(Long userId) {
        return !access.hasPlatformRole(userId,"PLATFORM_ADMIN")&&(access.hasAnyCompanyRole(userId,"COMPANY_ADMIN")
            ||access.hasAnyCompanyRole(userId,"PROJECT_ADMIN")||access.hasAnyProjectRole(userId,"PROJECT_MANAGER")||access.hasAnyProjectRole(userId,"PROJECT_ADMIN"));
    }

    public boolean canManageProjects(Long userId) {
        return access.hasAnyProjectRole(userId, "PROJECT_ADMIN") || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM role_assignments r JOIN company_memberships m " +
                "ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.user_id=? " +
                "AND r.role_key='PROJECT_ADMIN' AND r.project_id IS NULL AND r.removed_at IS NULL " +
                "AND m.status='ACTIVE')", Boolean.class, userId));
    }

    public boolean canApproveProjectManagerHours(Long projectId, Long approverId) {
        return access.hasProjectRole(projectId, approverId, "PROJECT_ADMIN");
    }

    private void requireProjectAdmin(Long projectId, User user) {
        if (user == null || access.hasPlatformRole(user.getId(),"PLATFORM_ADMIN") || !access.mayManageProject(projectId, user.getId()))
            throw new org.springframework.security.access.AccessDeniedException("Project Admin permission required");
    }

    private boolean canViewManagementProject(Project project, long userId) {
        return !access.hasPlatformRole(userId,"PLATFORM_ADMIN") && (access.hasCompanyRole(project.getCompanyId(), userId, "COMPANY_ADMIN")
                || access.hasCompanyRole(project.getCompanyId(),userId,"PROJECT_ADMIN")
                || access.hasProjectRole(project.getId(), userId, "PROJECT_ADMIN")
                || access.hasProjectRole(project.getId(), userId, "PROJECT_MANAGER")
                || access.hasModeratorGrant(project.getId(), userId, false)
                || access.hasModeratorGrant(project.getId(), userId, true));
    }

    private void requireCanPlanProject(Long projectId, User requester) {
        if (requester == null || !access.mayManageProject(projectId, requester.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Project planning permission required");
        }
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
                .companyId(project.getCompanyId())
                .ownerUserId(project.getOwnerUserId())
                .code(project.getCode())
                .name(project.getName())
                .description(project.getDescription())
                .isActive(project.getIsActive())
                .status(project.getStatus())
                .totalAllocatedHours(project.getTotalAllocatedHours())
                .expenseBudget(project.getExpenseBudget())
                .approvalFrequency(activeApprovalFrequency(project))
                .pendingApprovalFrequency(project.getApprovalFrequencyEffectiveOn() != null
                        && project.getApprovalFrequencyEffectiveOn().isAfter(LocalDate.now()) ? project.getPendingApprovalFrequency() : null)
                .approvalFrequencyEffectiveOn(project.getApprovalFrequencyEffectiveOn() != null
                        && project.getApprovalFrequencyEffectiveOn().isAfter(LocalDate.now()) ? project.getApprovalFrequencyEffectiveOn() : null)
                .pendingApprovalCount(jdbc.queryForObject("SELECT count(*) FROM timesheet_approval_periods WHERE project_id=? AND status='SUBMITTED'", Long.class, project.getId()))
                .projectManagerId(project.getProjectManager() != null ? project.getProjectManager().getId() : null)
                .projectManagerName(project.getProjectManager() != null ? project.getProjectManager().getFullName() : null)
                .projectManagerHoursApproverId(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getId() : null)
                .projectManagerHoursApproverName(project.getProjectManagerHoursApprover() != null ? project.getProjectManagerHoursApprover().getFullName() : null)
                .assignments(assignments)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private String activeApprovalFrequency(Project project) {
        if (project.getId() == null) return project.getApprovalFrequency();
        return jdbc.query("SELECT frequency FROM project_approval_frequency_changes WHERE project_id=? AND effective_on<=? ORDER BY effective_on DESC LIMIT 1",
                (rs, row) -> rs.getString(1), project.getId(), java.time.LocalDate.now()).stream()
                .findFirst().orElse(project.getApprovalFrequency());
    }

    private ProjectAssignmentDTO toAssignmentDTO(ProjectAssignment assignment) {
        return toAssignmentDTO(assignment, null, null);
    }

    private ProjectAssignmentDTO toAssignmentDTO(ProjectAssignment assignment, Integer year, Integer month) {
        User user = assignment.getUser();
        BigDecimal plannedHours = assignment.getPlannedHours();
        if (plannedHours == null && year != null && month != null) {
            plannedHours = hourPlanRepository
                    .findByProjectIdAndUserIdAndYearAndMonth(assignment.getProject().getId(), user.getId(), year, month)
                    .map(plan -> plan.getPlannedHours() != null ? plan.getPlannedHours() : BigDecimal.ZERO)
                    .orElse(plannedHours);
        }
        boolean active = Boolean.TRUE.equals(assignment.getIsActive());
        BigDecimal approvedToDate = approvedHoursToDate(assignment.getProject().getId(), user.getId());
        if (!active) plannedHours = assignment.getPlannedHours() != null ? assignment.getPlannedHours() : approvedToDate;
        return ProjectAssignmentDTO.builder()
                .id(assignment.getId())
                .userId(user.getId())
                .canApproveHours(access.hasModeratorGrant(assignment.getProject().getId(),user.getId(),false))
                .canApproveExpenses(access.hasModeratorGrant(assignment.getProject().getId(),user.getId(),true))
                .employeeId(access.employmentEmployeeId(assignment.getProject().getCompanyId(),user.getId()))
                .userName(user.getFullName())
                .email(user.getEmail())
                .jobTitle(access.employmentJobTitle(assignment.getProject().getCompanyId(),user.getId()))
                .isActive(assignment.getIsActive())
                .plannedHours(plannedHours)
                .approvedHoursToDate(active ? approvedToDate : plannedHours)
                .pendingApproval(projectSubmissionRepository.findByProjectIdAndTimesheetUserId(assignment.getProject().getId(), user.getId()).stream().anyMatch(this::pendingApproval))
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
                .budgetHours(project.getTotalAllocatedHours())
                .lifetimeLoggedHours(projectRepository.totalRecordedHours(project.getId()))
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

        Map<String, BigDecimal> periodHours = new HashMap<>();
        if (timesheet != null) jdbc.query("SELECT COALESCE(a.status,'DRAFT') AS status, COALESCE(sum(e.hours),0) AS hours " +
                        "FROM time_entries e LEFT JOIN timesheet_approval_periods a ON a.project_id=e.project_id " +
                        "AND a.user_id=? AND e.entry_date BETWEEN a.period_start AND a.period_end " +
                        "WHERE e.timesheet_id=? AND e.project_id=? GROUP BY COALESCE(a.status,'DRAFT')",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        periodHours.put(rs.getString("status"), rs.getBigDecimal("hours")),
                user.getId(), timesheet.getId(), project.getId());
        TimesheetStatus status = periodHours.containsKey("SUBMITTED") ? TimesheetStatus.SUBMITTED
                : periodHours.containsKey("REJECTED") ? TimesheetStatus.REJECTED
                : periodHours.containsKey("DRAFT") ? TimesheetStatus.DRAFT
                : periodHours.containsKey("APPROVED") ? TimesheetStatus.APPROVED : TimesheetStatus.DRAFT;
        return ProjectHoursEmployeeDTO.builder()
                .userId(user.getId())
                .employeeId(access.employmentEmployeeId(project.getCompanyId(),user.getId()))
                .userName(user.getFullName())
                .email(user.getEmail())
                .jobTitle(access.employmentJobTitle(project.getCompanyId(),user.getId()))
                .plannedHours(plannedHoursFor(assignment, plan))
                .draftHours(periodHours.getOrDefault("DRAFT", BigDecimal.ZERO))
                .submittedHours(periodHours.getOrDefault("SUBMITTED", BigDecimal.ZERO))
                .approvedHours(periodHours.getOrDefault("APPROVED", BigDecimal.ZERO))
                .rejectedHours(periodHours.getOrDefault("REJECTED", BigDecimal.ZERO))
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
