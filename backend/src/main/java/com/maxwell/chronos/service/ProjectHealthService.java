package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.ProjectHealthDTO;
import com.maxwell.chronos.dto.ProjectHealthDTO.Signal;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectHealthService {
    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final TimeEntryRepository entries;
    private final TimesheetProjectSubmissionRepository submissions;

    public List<ProjectHealthDTO> overview(User requester) {
        if (requester == null || !(requester.isAdmin() || requester.isSuperAdmin()
                || projectService.canReviewProjects(requester.getId()))) {
            throw new AccessDeniedException("Project review permission required");
        }
        Set<Long> visible = projectService.visibleProjectIds(requester);
        if (visible.isEmpty()) return List.of();
        var projectList = projects.findAllById(visible);
        var team = assignments.findForHealth(visible);
        var userIds = team.stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        // Other project assignments are used only for aggregate capacity, never exposed to the caller.
        var capacity = userIds.isEmpty() ? List.<ProjectAssignment>of() : assignments.findCapacityForHealth(userIds);
        LocalDate today = LocalDate.now();
        var logged = entries.findForHealth(visible, today).stream().collect(Collectors.groupingBy(e -> e.getProject().getId()));
        var submitted = submissions.findForHealth(visible).stream().collect(Collectors.groupingBy(s -> s.getProject().getId()));
        var teams = team.stream().collect(Collectors.groupingBy(a -> a.getProject().getId()));
        return projectList.stream().map(p -> evaluate(p, teams.getOrDefault(p.getId(), List.of()), capacity,
                        logged.getOrDefault(p.getId(), List.of()), submitted.getOrDefault(p.getId(), List.of()), today))
                .sorted(Comparator.comparingInt((ProjectHealthDTO h) -> rank(h.status())).reversed()
                        .thenComparing(ProjectHealthDTO::projectCode, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    static ProjectHealthDTO evaluate(Project project, List<ProjectAssignment> team, List<ProjectAssignment> capacity,
                                     List<TimeEntry> entries, List<TimesheetProjectSubmission> submissions, LocalDate today) {
        boolean monitored = project.getStatus() == ProjectStatus.ACTIVE;
        List<Signal> signals = new ArrayList<>();
        List<String> notes = new ArrayList<>(List.of(
                "Expense and monetary budgets, milestones, and project deadlines are not tracked in this workspace.",
                "Capacity estimates spread assigned hours evenly across weekdays, using 8 hours per day; leave and holidays are not included.",
                "Missing submissions cover completed months only; current-month drafts are not overdue."));
        if (!monitored) notes.add("Delivery alerts are paused for projects that are not active; outstanding timesheets are still checked.");
        BigDecimal logged = entries.stream().filter(e -> !e.getEntryDate().isAfter(today))
                .map(TimeEntry::getHours).map(ProjectHealthService::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal planned = team.stream().map(ProjectAssignment::getPlannedHours).map(ProjectHealthService::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = project.getTotalAllocatedHours() != null ? project.getTotalAllocatedHours()
                : team.stream().anyMatch(a -> a.getPlannedHours() != null) ? planned : null;
        if (project.getTotalAllocatedHours() == null && allocated != null) notes.add("Hour budget is derived from total resource assignments.");
        BigDecimal remaining = allocated == null ? null : allocated.subtract(logged);
        Double used = allocated != null && allocated.signum() > 0 ? logged.doubleValue() / allocated.doubleValue() * 100 : null;
        var active = team.stream().filter(a -> Boolean.TRUE.equals(a.getIsActive()) && Boolean.TRUE.equals(a.getUser().getIsActive())).toList();
        int currentTeam = (int) active.stream().filter(a -> within(a, today)).count();
        LocalDate start = team.stream().map(ProjectAssignment::getStartDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate end = active.stream().map(ProjectAssignment::getEndDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if (monitored) {
            if (allocated == null) add(signals, "HOURS_UNKNOWN", false, "Hour budget is not set");
            else if (logged.compareTo(allocated) > 0) add(signals, "HOURS_EXCEEDED", true, number(logged.subtract(allocated)) + " hours over the allocated budget");
            else if (used != null && used >= 80) add(signals, "HOURS_USED", used >= 95, Math.round(used) + "% of allocated hours used");
            if (planned.signum() > 0 && logged.compareTo(planned) > 0)
                add(signals, "PLAN_EXCEEDED", true, number(logged.subtract(planned)) + " logged hours above resource plans");
            if (allocated != null && planned.compareTo(allocated) > 0)
                add(signals, "PLAN_OVER_BUDGET", false, number(planned.subtract(allocated)) + " assigned hours exceed the project budget");
            if (currentTeam == 0 && (start == null || !today.isBefore(start)))
                add(signals, "NO_COVERAGE", true, "No resources assigned for today; potential understaffing");
            if (end != null) {
                long days = ChronoUnit.DAYS.between(today, end);
                if (days < 0) add(signals, "WINDOW_ENDED", true, "Assignment window ended " + -days + " days ago while the project remains active");
                else if (days <= 14) add(signals, "WINDOW_ENDING", days <= 7, "Assignment window ends in " + days + " days");
                if (start != null && end.isAfter(start) && !today.isBefore(start) && !today.isAfter(end) && used != null) {
                    double timeline = 100.0 * ChronoUnit.DAYS.between(today, end) / ChronoUnit.DAYS.between(start, end);
                    if (used >= 50 && used - (100 - timeline) >= 20)
                        add(signals, "BURN_AHEAD", used >= 80, Math.round(used) + "% of hours used with " + Math.round(timeline) + "% of assignment timeline remaining");
                }
            } else notes.add("Assignment timeline is unavailable until dated resource assignments exist.");
        }
        int overallocated = monitored ? (int) active.stream().map(a -> a.getUser().getId()).distinct()
                .filter(id -> overallocated(id, project.getId(), capacity, today)).count() : 0;
        if (overallocated > 0) add(signals, "CAPACITY", false, overallocated + " employee" + (overallocated == 1 ? " is" : "s are") + " potentially overallocated across overlapping assignments");

        Set<String> expected = new HashSet<>();
        YearMonth current = YearMonth.from(today);
        // Historical records remain actionable even when an assignment has ended.
        for (var entry : entries) if (zero(entry.getHours()).signum() > 0 && YearMonth.from(entry.getEntryDate()).isBefore(current))
            expected.add(key(entry.getTimesheet()));
        if (monitored) for (var a : active) {
            if (a.getStartDate() == null || a.getEndDate() == null || zero(a.getPlannedHours()).signum() <= 0) continue;
            LocalDate first = a.getStartDate();
            if (a.getAssignedAt() != null && a.getAssignedAt().toLocalDate().isAfter(first)) first = a.getAssignedAt().toLocalDate();
            for (YearMonth month = YearMonth.from(first); month.isBefore(current) && !month.atDay(1).isAfter(a.getEndDate()); month = month.plusMonths(1)) {
                LocalDate from = first.isAfter(month.atDay(1)) ? first : month.atDay(1);
                LocalDate through = a.getEndDate().isBefore(month.atEndOfMonth()) ? a.getEndDate() : month.atEndOfMonth();
                if (weekdays(from, through) > 0) expected.add(a.getUser().getId() + ":" + month);
            }
        }
        long pending = 0;
        long rejected = 0;
        for (var s : submissions) {
            if (s.getStatus() == TimesheetStatus.SUBMITTED || s.getStatus() == TimesheetStatus.CHANGE_REQUESTED) pending++;
            if (s.getStatus() == TimesheetStatus.REJECTED) rejected++;
            if (s.getStatus() != TimesheetStatus.DRAFT && s.getStatus() != TimesheetStatus.REJECTED) expected.remove(key(s.getTimesheet()));
            else if (YearMonth.of(s.getTimesheet().getYear(), s.getTimesheet().getMonth()).isBefore(current)
                    && zero(s.getTotalHours()).signum() > 0) expected.add(key(s.getTimesheet()));
        }
        if (!expected.isEmpty()) add(signals, "MISSING_SUBMISSIONS", false, expected.size() + " project timesheet" + (expected.size() == 1 ? "" : "s") + " awaiting submission for completed months");
        if (pending > 0) add(signals, "APPROVALS", false, pending + " project timesheet" + (pending == 1 ? "" : "s") + " awaiting approval");
        if (rejected > 0) add(signals, "REJECTED", false, rejected + " rejected project timesheet" + (rejected == 1 ? " needs" : "s need") + " correction and resubmission");
        String status = signals.stream().anyMatch(s -> s.severity().equals("AT_RISK")) ? "AT_RISK" : signals.isEmpty() ? "HEALTHY" : "ATTENTION_NEEDED";
        signals.sort(Comparator.comparingInt((Signal signal) -> rank(signal.severity())).reversed());
        return new ProjectHealthDTO(project.getId(), project.getCode(), project.getName(), status, today, monitored,
                allocated, planned, logged, remaining, used, end, currentTeam, overallocated, expected.size(), pending, List.copyOf(signals), List.copyOf(notes));
    }

    private static boolean overallocated(Long userId, Long projectId, List<ProjectAssignment> assignments, LocalDate today) {
        var relevant = assignments.stream().filter(a -> userId.equals(a.getUser().getId()) && Boolean.TRUE.equals(a.getIsActive())
                && a.getProject().getStatus() == ProjectStatus.ACTIVE && a.getStartDate() != null && a.getEndDate() != null
                && !a.getEndDate().isBefore(today) && weekdays(a.getStartDate(), a.getEndDate()) > 0).toList();
        // Allocation changes only at interval boundaries. Evaluate weekday boundaries, not every calendar day.
        Set<LocalDate> boundaries = new HashSet<>(Set.of(nextWeekday(today)));
        relevant.forEach(a -> boundaries.add(nextWeekday(a.getStartDate().isBefore(today) ? today : a.getStartDate())));
        return boundaries.stream().anyMatch(day -> relevant.stream().anyMatch(a -> projectId.equals(a.getProject().getId()) && within(a, day))
                && relevant.stream().filter(a -> within(a, day)).mapToDouble(a -> zero(a.getPlannedHours()).doubleValue() / weekdays(a.getStartDate(), a.getEndDate())).sum() > 8.0001);
    }
    private static LocalDate nextWeekday(LocalDate date) {
        while (date.getDayOfWeek().getValue() > 5) date = date.plusDays(1);
        return date;
    }
    static long weekdays(LocalDate from, LocalDate through) {
        if (through.isBefore(from)) return 0;
        long days = ChronoUnit.DAYS.between(from, through) + 1;
        long count = days / 7 * 5;
        for (int i = 0; i < days % 7; i++) if (from.plusDays(i).getDayOfWeek().getValue() <= 5) count++;
        return count;
    }
    private static boolean within(ProjectAssignment a, LocalDate day) {
        return a.getStartDate() != null && a.getEndDate() != null && !day.isBefore(a.getStartDate()) && !day.isAfter(a.getEndDate());
    }
    private static String key(Timesheet t) { return t.getUser().getId() + ":" + YearMonth.of(t.getYear(), t.getMonth()); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static int rank(String status) { return status.equals("AT_RISK") ? 2 : status.equals("ATTENTION_NEEDED") ? 1 : 0; }
    private static void add(List<Signal> signals, String code, boolean risk, String message) {
        signals.add(new Signal(code, risk ? "AT_RISK" : "ATTENTION_NEEDED", message));
    }
}
