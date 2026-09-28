package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.LeaveAllowance;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.LeaveAllowanceRequest;
import com.maxwell.chronos.dto.LeaveBalanceDTO;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.enums.VacationType;
import com.maxwell.chronos.enums.LeaveAccountingType;
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
    private final LeavePolicyYearRepository policies;

    public LeaveBalanceDTO getBalance(Long userId, int year, User requester) {
        authorize(userId, requester);
        validateYear(year);
        var allowance = ensureAllowance(userId, year);
        var value = allowance.orElseGet(LeaveAllowance::new);
        return new LeaveBalanceDTO(year, allowance.isPresent(),
            calculate(userId, year, VacationType.VACATION, value.getVacationDays(), value.getExtraVacationDays()),
            calculate(userId, year, VacationType.SICK, value.getSickDays(), value.getExtraSickDays()),
            calculate(userId, year, VacationType.BEREAVEMENT, value.getBereavementDays(), BigDecimal.ZERO),
            allowance.map(LeaveAllowance::getSource).orElse(null));
    }

    public LeaveBalanceDTO update(Long userId, LeaveAllowanceRequest input, User requester) {
        if (requester == null || !requester.isAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only Admin can set leave allowances");
        validateYear(input.year());
        var employee = users.findForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (employee.isAdmin()) throw new IllegalArgumentException("Leave allowances apply to employees and Project Admins");
        var value = allowances.findByUserIdAndYear(userId, input.year()).orElseGet(LeaveAllowance::new);
        value.setUserId(userId); value.setYear(input.year());
        boolean matchesPolicy = policies.findById(input.year()).map(policy ->
                policy.getVacationDays().compareTo(input.vacationDays()) == 0
                && policy.getSickDays().compareTo(input.sickDays()) == 0
                && policy.getBereavementDays().compareTo(input.bereavementDays() == null ? value.getBereavementDays() : input.bereavementDays()) == 0)
                .orElse(false);
        value.setSource(matchesPolicy && (value.getId() == null || "POLICY".equals(value.getSource())) ? "POLICY" : "OVERRIDE");
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

    public void validateApproval(com.maxwell.chronos.domain.VacationRequest request, LeaveAccountingType accounting) {
        VacationType bucket = switch (accounting) {
            case VACATION -> VacationType.VACATION;
            case SICK -> VacationType.SICK;
            case BEREAVEMENT -> VacationType.BEREAVEMENT;
            default -> null;
        };
        if (bucket == null) return;
        for (int year = request.getStartDate().getYear(); year <= request.getEndDate().getYear(); year++) {
            var allowance = ensureAllowance(request.getUser().getId(), year);
            if (allowance.isEmpty()) throw new IllegalArgumentException("Leave allowance is not set for " + year);
            var value = allowance.get();
            var balance = calculate(request.getUser().getId(), year, bucket,
                    bucket == VacationType.SICK ? value.getSickDays() : bucket == VacationType.BEREAVEMENT ? value.getBereavementDays() : value.getVacationDays(),
                    bucket == VacationType.SICK ? value.getExtraSickDays() : bucket == VacationType.BEREAVEMENT ? BigDecimal.ZERO : value.getExtraVacationDays());
            var start = request.getStartDate().isBefore(LocalDate.of(year, 1, 1)) ? LocalDate.of(year, 1, 1) : request.getStartDate();
            var end = request.getEndDate().isAfter(LocalDate.of(year, 12, 31)) ? LocalDate.of(year, 12, 31) : request.getEndDate();
            long days = start.datesUntil(end.plusDays(1)).filter(day -> day.getDayOfWeek().getValue() < 6).count();
            if (balance.remainingDays().compareTo(BigDecimal.valueOf(days)) < 0)
                throw new IllegalArgumentException("Not enough " + bucket.name().toLowerCase() + " leave for " + year + "; choose unpaid leave or update the allowance");
        }
    }

    private LeaveBalanceDTO.Balance calculate(Long userId, int year, VacationType type, BigDecimal base, BigDecimal extra) {
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        var dates = new HashSet<LocalDate>();
        var pendingDates = new HashSet<LocalDate>();
        requests.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(userId, end, start).stream()
            .filter(r -> bucket(r) == type && (r.getStatus() == VacationStatus.APPROVED || r.getStatus() == VacationStatus.LOCKED || r.getStatus() == VacationStatus.SUBMITTED))
            .forEach(r -> {
                LocalDate first = r.getStartDate().isBefore(start) ? start : r.getStartDate();
                LocalDate last = r.getEndDate().isAfter(end) ? end : r.getEndDate();
                first.datesUntil(last.plusDays(1)).filter(d -> d.getDayOfWeek().getValue() < 6)
                        .forEach((r.getStatus() == VacationStatus.SUBMITTED ? pendingDates : dates)::add);
            });
        BigDecimal used = BigDecimal.valueOf(dates.size()), total = base.add(extra);
        return new LeaveBalanceDTO.Balance(base, extra, used, total.subtract(used).max(BigDecimal.ZERO),
                used.subtract(total).max(BigDecimal.ZERO), BigDecimal.valueOf(pendingDates.size()));
    }
    private VacationType bucket(com.maxwell.chronos.domain.VacationRequest request) {
        if (request.getAccountingType() != null) return switch (request.getAccountingType()) {
            case VACATION -> VacationType.VACATION;
            case SICK -> VacationType.SICK;
            case BEREAVEMENT -> VacationType.BEREAVEMENT;
            default -> null;
        };
        VacationType type = request.getVacationType();
        if (type == null || type == VacationType.UNPAID_LEAVE) return null;
        if (type == VacationType.SICK || type == VacationType.BEREAVEMENT) return type;
        if (type == VacationType.SPECIAL) return null;
        if (type != VacationType.VACATION && request.getStatus() != VacationStatus.APPROVED && request.getStatus() != VacationStatus.LOCKED)
            return null;
        return VacationType.VACATION;
    }
    private java.util.Optional<LeaveAllowance> ensureAllowance(Long userId, int year) {
        var existing = allowances.findByUserIdAndYear(userId, year);
        if (existing.isPresent()) return existing;
        var policy = policies.findById(year);
        if (policy.isEmpty()) return existing;
        var employee = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (employee.isAdmin() || Boolean.FALSE.equals(employee.getIsActive()) || employee.getJoiningDate() != null && employee.getJoiningDate().getYear() > year) return existing;
        var allowance = new LeaveAllowance();
        allowance.setUserId(userId); allowance.setYear(year); allowance.setSource("POLICY");
        allowance.setVacationDays(policy.get().getVacationDays());
        allowance.setSickDays(policy.get().getSickDays());
        allowance.setBereavementDays(policy.get().getBereavementDays());
        return java.util.Optional.of(allowances.save(allowance));
    }
    private void authorize(Long userId, User requester) {
        if (requester == null || (!requester.isAdmin() && !requester.getId().equals(userId)))
            throw new org.springframework.security.access.AccessDeniedException("Leave balance permission required");
    }
    private void validateYear(int year) {
        if (year < 1900 || year > 9998) throw new IllegalArgumentException("Year must be between 1900 and 9998");
    }
}
