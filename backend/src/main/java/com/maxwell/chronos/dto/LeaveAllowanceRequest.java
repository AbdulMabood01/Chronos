package com.maxwell.chronos.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record LeaveAllowanceRequest(
    @Min(1900) @Max(9998) int year,
    @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3, fraction=2) BigDecimal vacationDays,
    @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3, fraction=2) BigDecimal sickDays,
    @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3, fraction=2) BigDecimal addVacationDays,
    @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3, fraction=2) BigDecimal addSickDays,
    @NotBlank @Size(max=500) String reason,
    @DecimalMin("0") @DecimalMax("366") @Digits(integer=3, fraction=2) BigDecimal bereavementDays) {
    public LeaveAllowanceRequest(int year, BigDecimal vacationDays, BigDecimal sickDays,
            BigDecimal addVacationDays, BigDecimal addSickDays, String reason) {
        this(year, vacationDays, sickDays, addVacationDays, addSickDays, reason, null);
    }
}
