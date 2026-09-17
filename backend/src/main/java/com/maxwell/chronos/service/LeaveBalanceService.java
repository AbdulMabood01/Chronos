package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.LeaveAllowance;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.LeaveAllowanceRequest;
import com.maxwell.chronos.dto.LeaveBalanceDTO;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.enums.VacationType;
import com.maxwell.chronos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

@Service @RequiredArgsConstructor @Transactional
public class LeaveBalanceService {
    private final LeaveAllowanceRepository allowances;
    private final VacationRequestRepository requests;
    private final UserRepository users;
    private final AuditService audit;

    public LeaveBalanceDTO getBalance(Long userId, int year, User requester) {
        authorize(userId, requester);
        validateYear(year);
        var allowance = allowances.findByUserIdAndYear(userId, year);
        var value = allowance.orElseGet(LeaveAllowance::new);
        return new LeaveBalanceDTO(year, allowance.isPresent(),
            calculate(userId, year, VacationType.VACATION, value.getVacationDays(), value.getExtraVacationDays()),
            calculate(userId, year, VacationType.SICK, value.getSickDays(), value.getExtraSickDays()),
            calculate(userId, year, VacationType.BEREAVEMENT, value.getBereavementDays(), BigDecimal.ZERO));
    }

    public LeaveBalanceDTO update(Long userId, LeaveAllowanceRequest input, User requester) {
        if (requester == null || !requester.isSuperAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only Super Admin can set leave allowances");
        validateYear(input.year());
        var employee = users.findForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (employee.isSuperAdmin()) throw new IllegalArgumentException("Leave allowances apply to employees and admins");
        var value = allowances.findByUserIdAndYear(userId, input.year()).orElseGet(LeaveAllowance::new);
        value.setUserId(userId); value.setYear(input.year());
        value.setVacationDays(input.vacationDays()); value.setSickDays(input.sickDays());
        value.setExtraVacationDays(value.getExtraVacationDays().add(input.addVacationDays()));
        value.setExtraSickDays(value.getExtraSickDays().add(input.addSickDays()));
        if (input.bereavementDays() != null) value.setBereavementDays(input.bereavementDays());
        allowances.save(value);
        audit.logAction(requester.getId(), "LEAVE_ALLOWANCE_UPDATED", "User", userId,
            "Year: " + input.year() + "; vacation: " + input.vacationDays() + "; sick: " + input.sickDays()
            + "; extra vacation added: " + input.addVacationDays() + "; extra sick added: " + input.addSickDays()
            + "; reason: " + input.reason());
        return getBalance(userId, input.year(), requester);
    }

    private LeaveBalanceDTO.Balance calculate(Long userId, int year, VacationType type, BigDecimal base, BigDecimal extra) {
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        var dates = new HashSet<LocalDate>();
        requests.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(userId, end, start).stream()
            .filter(r -> bucket(r.getVacationType()) == type && (r.getStatus() == VacationStatus.APPROVED || r.getStatus() == VacationStatus.LOCKED))
            .forEach(r -> {
                LocalDate first = r.getStartDate().isBefore(start) ? start : r.getStartDate();
                LocalDate last = r.getEndDate().isAfter(end) ? end : r.getEndDate();
                first.datesUntil(last.plusDays(1)).filter(d -> d.getDayOfWeek().getValue() < 6).forEach(dates::add);
            });
        BigDecimal used = BigDecimal.valueOf(dates.size()), total = base.add(extra);
        return new LeaveBalanceDTO.Balance(base, extra, used, total.subtract(used).max(BigDecimal.ZERO), used.subtract(total).max(BigDecimal.ZERO));
    }
    private VacationType bucket(VacationType type) {
        if (type == null || type == VacationType.UNPAID_LEAVE) return null;
        if (type == VacationType.SICK || type == VacationType.BEREAVEMENT) return type;
        return VacationType.VACATION;
    }
    private void authorize(Long userId, User requester) {
        if (requester == null || (!requester.isSuperAdmin() && !requester.getId().equals(userId)))
            throw new org.springframework.security.access.AccessDeniedException("Leave balance permission required");
    }
    private void validateYear(int year) {
        if (year < 1900 || year > 9998) throw new IllegalArgumentException("Year must be between 1900 and 9998");
    }
}
