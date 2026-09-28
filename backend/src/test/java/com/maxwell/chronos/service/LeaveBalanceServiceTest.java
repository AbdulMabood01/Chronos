package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.LeaveAllowanceRequest;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceTest {
    @Mock LeaveAllowanceRepository allowances;
    @Mock VacationRequestRepository requests;
    @Mock UserRepository users;
    @Mock AuditService audit;
    @Mock LeavePolicyYearRepository policies;
    @InjectMocks LeaveBalanceService service;
    User employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
    User admin = User.builder().id(2L).role(UserRole.ADMIN).build();
    VacationRequest request(String start, String end, VacationType type, VacationStatus status) {
        return VacationRequest.builder().user(employee).startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).vacationType(type).status(status).build();
    }
    void stubRequests(VacationRequest... values) {
        when(requests.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(eq(1L), any(), any())).thenReturn(List.of(values));
    }
    @Test void approvalConsumesBaseThenExtraAndExcessIsUnpaid() {
        var allowance = new LeaveAllowance(); allowance.setVacationDays(BigDecimal.valueOf(2)); allowance.setExtraVacationDays(BigDecimal.ONE);
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(allowance));
        stubRequests(request("2026-09-07", "2026-09-11", VacationType.VACATION, VacationStatus.APPROVED),
            request("2026-09-14", "2026-09-18", VacationType.VACATION, VacationStatus.SUBMITTED),
            request("2026-09-21", "2026-09-25", VacationType.VACATION, VacationStatus.REJECTED));
        var balance = service.getBalance(1L, 2026, employee).vacation();
        assertEquals(0, balance.usedDays().compareTo(BigDecimal.valueOf(5)));
        assertEquals(0, balance.remainingDays().compareTo(BigDecimal.ZERO));
        assertEquals(0, balance.unpaidDays().compareTo(BigDecimal.valueOf(2)));
        assertEquals(balance, service.getBalance(1L, 2026, employee).vacation());
    }
    @Test void splitsYearsSkipsWeekendsAndDoesNotDoubleCountSameDate() {
        stubRequests(request("2025-12-29", "2026-01-04", VacationType.SICK, VacationStatus.LOCKED),
            request("2026-01-02", "2026-01-02", VacationType.SICK, VacationStatus.APPROVED));
        var balance = service.getBalance(1L, 2026, employee);
        assertEquals(0, balance.sick().usedDays().compareTo(BigDecimal.valueOf(2)));
        assertEquals(0, balance.vacation().usedDays().compareTo(BigDecimal.ZERO));
    }
    @Test void onlyAdminMayGrantDays() {
        var input = new LeaveAllowanceRequest(2026, BigDecimal.TEN, BigDecimal.valueOf(5), BigDecimal.ONE, BigDecimal.ZERO, "Annual allowance");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.update(1L, input, employee));
        verifyNoInteractions(allowances);
    }
    @Test void savesSeparateAllowancesAndAddsToExistingExtraDays() {
        var allowance = new LeaveAllowance(); allowance.setExtraVacationDays(BigDecimal.valueOf(2));
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(allowance));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        stubRequests();
        service.update(1L, new LeaveAllowanceRequest(2026, BigDecimal.TEN, BigDecimal.valueOf(5), BigDecimal.ONE, BigDecimal.ZERO, "Extra day"), admin);
        assertEquals(BigDecimal.valueOf(3), allowance.getExtraVacationDays());
        assertEquals(BigDecimal.valueOf(5), allowance.getSickDays());
        verify(allowances).save(allowance);
        verify(audit).logAction(eq(2L), eq("LEAVE_ALLOWANCE_UPDATED"), eq("User"), eq(1L), anyString());
    }
    @Test void cannotReadAnotherEmployeesBalance() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getBalance(3L, 2026, employee));
        verifyNoInteractions(allowances, requests);
    }
    @Test void routesAllPaidCategoriesAndRecalculatesAfterChanges() {
        var sick = request("2026-09-07", "2026-09-07", VacationType.SICK, VacationStatus.APPROVED);
        var bereavement = request("2026-09-08", "2026-09-08", VacationType.BEREAVEMENT, VacationStatus.APPROVED);
        var personal = request("2026-09-09", "2026-09-09", VacationType.PERSONAL, VacationStatus.APPROVED);
        stubRequests(sick, bereavement, personal, request("2026-09-10", "2026-09-10", VacationType.UNPAID_LEAVE, VacationStatus.APPROVED));
        var balance = service.getBalance(1L, 2026, employee);
        assertEquals(BigDecimal.ONE, balance.sick().usedDays());
        assertEquals(BigDecimal.ONE, balance.bereavement().usedDays());
        assertEquals(BigDecimal.ONE, balance.vacation().usedDays());
        personal.setVacationType(VacationType.SICK);
        bereavement.setStatus(VacationStatus.REJECTED);
        balance = service.getBalance(1L, 2026, employee);
        assertEquals(BigDecimal.valueOf(2), balance.sick().usedDays());
        assertEquals(BigDecimal.ZERO, balance.bereavement().usedDays());
        assertEquals(BigDecimal.ZERO, balance.vacation().usedDays());
        assertEquals(balance, service.getBalance(1L, 2026, employee));
    }

    @Test void specialLeaveDoesNotConsumeQuotaUnlessReviewerChoosesABalance() {
        var special = request("2026-09-07", "2026-09-08", VacationType.SPECIAL, VacationStatus.APPROVED);
        special.setAccountingType(LeaveAccountingType.PAID_NO_QUOTA);
        stubRequests(special);
        assertEquals(BigDecimal.ZERO, service.getBalance(1L, 2026, employee).vacation().usedDays());
        special.setAccountingType(LeaveAccountingType.VACATION);
        assertEquals(BigDecimal.valueOf(2), service.getBalance(1L, 2026, employee).vacation().usedDays());
    }

    @Test void approvalChecksTheCurrentAllowanceAndKeepsExtraGrantPolicyManaged() {
        var policy = new LeavePolicyYear(); policy.setYear(2026);
        policy.setVacationDays(BigDecimal.valueOf(2)); policy.setSickDays(BigDecimal.valueOf(5));
        policy.setBereavementDays(BigDecimal.ONE);
        var allowance = new LeaveAllowance(); allowance.setId(3L); allowance.setSource("POLICY");
        allowance.setVacationDays(BigDecimal.valueOf(2));
        when(allowances.findByUserIdAndYear(1L, 2026)).thenReturn(Optional.of(allowance));
        when(policies.findById(2026)).thenReturn(Optional.of(policy));
        stubRequests();
        var request = request("2026-09-07", "2026-09-09", VacationType.VACATION, VacationStatus.SUBMITTED);
        assertThrows(IllegalArgumentException.class, () -> service.validateApproval(request, LeaveAccountingType.VACATION));
        assertDoesNotThrow(() -> service.validateApproval(request, LeaveAccountingType.PAID_NO_QUOTA));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        service.update(1L, new LeaveAllowanceRequest(2026, BigDecimal.valueOf(2), BigDecimal.valueOf(5),
                BigDecimal.valueOf(2), BigDecimal.ZERO, "Extra grant", BigDecimal.ONE), admin);
        assertEquals("POLICY", allowance.getSource());
        assertEquals(BigDecimal.valueOf(2), allowance.getExtraVacationDays());
        assertDoesNotThrow(() -> service.validateApproval(request, LeaveAccountingType.VACATION));
    }

    @Test void publishedYearPolicyAssignsAnEligibleEmployeeOnFirstBalanceRead() {
        var policy = new LeavePolicyYear(); policy.setYear(2026);
        policy.setVacationDays(BigDecimal.valueOf(15)); policy.setSickDays(BigDecimal.valueOf(5));
        policy.setBereavementDays(BigDecimal.valueOf(3));
        when(policies.findById(2026)).thenReturn(Optional.of(policy));
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(allowances.save(any(LeaveAllowance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubRequests();

        var balance = service.getBalance(1L, 2026, employee);
        assertTrue(balance.configured());
        assertEquals("POLICY", balance.source());
        assertEquals(BigDecimal.valueOf(15), balance.vacation().remainingDays());
        verify(allowances).save(argThat(value -> value.getUserId().equals(1L)
                && "POLICY".equals(value.getSource()) && value.getYear() == 2026));
    }
}
