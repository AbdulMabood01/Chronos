package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamCalendarServiceTest {
    final ProjectService projects = mock(ProjectService.class);
    final ProjectAssignmentRepository assignments = mock(ProjectAssignmentRepository.class);
    final VacationRequestRepository requests = mock(VacationRequestRepository.class);
    final TeamCalendarService service = new TeamCalendarService(projects, assignments, requests);
    final User manager = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
    final User alice = User.builder().id(2L).firstName("Alice").lastName("Smith").role(UserRole.EMPLOYEE).build();
    final User bob = User.builder().id(3L).firstName("Bob").lastName("Jones").role(UserRole.EMPLOYEE).build();
    @Test void managerSeesOnlyTeamAbsencesIncludingCrossMonthLeave() {
        when(projects.canReviewProjects(1L)).thenReturn(true);
        when(projects.visibleProjectIds(manager)).thenReturn(Set.of(4L));
        var project = Project.builder().id(4L).projectManager(manager).build();
        when(assignments.findAll()).thenReturn(List.of(ProjectAssignment.builder().project(project).user(alice).isActive(true)
                .startDate(LocalDate.of(2026,1,1)).endDate(LocalDate.of(2026,12,31)).build()));
        var leave = VacationRequest.builder().id(5L).user(alice).startDate(LocalDate.of(2026,8,31)).endDate(LocalDate.of(2026,9,2)).status(VacationStatus.APPROVED).notes("Private medical information").vacationType(VacationType.SICK).build();
        var other = VacationRequest.builder().id(6L).user(bob).startDate(LocalDate.of(2026,9,1)).endDate(LocalDate.of(2026,9,2)).status(VacationStatus.LOCKED).build();
        when(requests.findByStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(List.of(VacationStatus.APPROVED, VacationStatus.LOCKED),LocalDate.of(2026,9,30),LocalDate.of(2026,9,1))).thenReturn(List.of(leave,other));
        var result = service.getCalendar(2026,9,manager);
        assertEquals(1,result.size());
        assertEquals("Alice Smith",result.get(0).userName());
        assertEquals(LocalDate.of(2026,8,31),result.get(0).startDate());
        assertEquals(Set.of("id","userId","userName","startDate","endDate"), Arrays.stream(TeamCalendarService.Absence.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
        manager.setRole(UserRole.ADMIN);
        assertEquals(2,service.getCalendar(2026,9,manager).size());
    }
    @Test void unrelatedEmployeesCannotReadCalendar() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getCalendar(2026,9,manager));
        verifyNoInteractions(requests);
    }
}
