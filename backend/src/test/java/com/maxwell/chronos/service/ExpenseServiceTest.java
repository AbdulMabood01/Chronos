package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.ProjectAssignmentRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    @Mock JdbcTemplate db;
    @Mock ProjectRepository projects;
    @Mock ProjectAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock CompanyAccessService access;
    @InjectMocks ExpenseService service;

    private final User employee = User.builder().id(7L).role(UserRole.EMPLOYEE).build();
    private final User manager = User.builder().id(8L).role(UserRole.EMPLOYEE).build();
    private final Project project = Project.builder().id(3L).code("ATLAS").isActive(true).projectManager(manager).build();

    @Test void cannotSubmitAgainstAnUnassignedProject() {
        when(users.findByEmailIgnoreCase("employee@test.com")).thenReturn(Optional.of(employee));
        when(projects.findById(3L)).thenReturn(Optional.of(project));
        var input = new ExpenseService.Input(3L,"TRAVEL",new BigDecimal("24.50"),LocalDate.now(),"Train fare");
        assertThrows(AccessDeniedException.class, () -> service.submit("employee@test.com",input,null));
        verifyNoInteractions(db,notifications);
    }

    @Test void rejectsInvalidMoneyAndOtherWithoutDetails() {
        when(users.findByEmailIgnoreCase("employee@test.com")).thenReturn(Optional.of(employee));
        assertThrows(IllegalArgumentException.class, () -> service.submit("employee@test.com",
                new ExpenseService.Input(3L,"TRAVEL",new BigDecimal("1.234"),LocalDate.now(),"Fare"),null));
        assertThrows(IllegalArgumentException.class, () -> service.submit("employee@test.com",
                new ExpenseService.Input(3L,"OTHER",BigDecimal.TEN,LocalDate.now(),"Other"),null));
        verifyNoInteractions(db,projects);
    }

    @Test void unrelatedEmployeeCannotReadOrApproveExpense() {
        var outsider = User.builder().id(9L).role(UserRole.EMPLOYEE).build();
        when(users.findByEmailIgnoreCase("other@test.com")).thenReturn(Optional.of(outsider));
        Map<String,Object> row = new HashMap<>();
        row.put("id", 12L); row.put("project_id", 3L); row.put("employee_id", 7L); row.put("status", "PENDING_APPROVAL");
        when(db.queryForList(startsWith("SELECT e.*"),eq(12L))).thenReturn(List.of(row));
        when(projects.findById(3L)).thenReturn(Optional.of(project));
        assertThrows(AccessDeniedException.class, () -> service.detail("other@test.com",12L));
        assertThrows(AccessDeniedException.class, () -> service.decide("other@test.com",12L,new ExpenseService.Decision("APPROVED",null)));
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test void rejectionRequiresAReviewerComment() {
        when(users.findByEmailIgnoreCase("manager@test.com")).thenReturn(Optional.of(manager));
        Map<String,Object> row = new HashMap<>();
        row.put("id", 12L); row.put("project_id", 3L); row.put("employee_id", 7L); row.put("status", "PENDING_APPROVAL");
        when(db.queryForList(startsWith("SELECT e.*"),eq(12L))).thenReturn(List.of(row));
        when(projects.findById(3L)).thenReturn(Optional.of(project));
        assertThrows(IllegalArgumentException.class, () -> service.decide("manager@test.com",12L,new ExpenseService.Decision("REJECTED"," ")));
        verifyNoInteractions(notifications);
    }
}
