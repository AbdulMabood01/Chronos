package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.VacationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Comparator;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class TeamCalendarService {
    private final ProjectService projects;
    private final ProjectAssignmentRepository assignments;
    private final VacationRequestRepository requests;
    public record Absence(Long id, Long userId, String userName, LocalDate startDate, LocalDate endDate) {}

    public List<Absence> getCalendar(int year, int month, User requester) {
        if (requester == null || (!requester.isAdmin() && !requester.isSuperAdmin() && !projects.canManageProjects(requester.getId())))
            throw new AccessDeniedException("Team calendar requires manager permission");
        YearMonth period = YearMonth.of(year, month);
        boolean allTeams = requester.isAdmin() || requester.isSuperAdmin();
        var projectIds = allTeams ? java.util.Set.<Long>of() : projects.visibleProjectIds(requester);
        var team = allTeams ? List.<com.maxwell.chronos.domain.ProjectAssignment>of() : assignments.findAll().stream()
                .filter(a -> projectIds.contains(a.getProject().getId()))
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()) || a.getEndDate() != null)
                .toList();
        return requests.findByStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                List.of(VacationStatus.APPROVED, VacationStatus.LOCKED), period.atEndOfMonth(), period.atDay(1)).stream()
                .filter(r -> allTeams || team.stream().anyMatch(a -> a.getUser().getId().equals(r.getUser().getId())
                        && (a.getStartDate() == null || !a.getStartDate().isAfter(r.getEndDate().isBefore(period.atEndOfMonth()) ? r.getEndDate() : period.atEndOfMonth()))
                        && (a.getEndDate() == null || !a.getEndDate().isBefore(r.getStartDate().isAfter(period.atDay(1)) ? r.getStartDate() : period.atDay(1)))))
                .map(r -> new Absence(r.getId(), r.getUser().getId(), r.getUser().getFullName(), r.getStartDate(), r.getEndDate()))
                .sorted(Comparator.comparing(Absence::startDate).thenComparing(Absence::userName)).toList();
    }
}
