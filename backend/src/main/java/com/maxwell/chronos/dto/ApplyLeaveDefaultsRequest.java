package com.maxwell.chronos.dto;
import jakarta.validation.constraints.*;
public record ApplyLeaveDefaultsRequest(@Min(1900) @Max(9998) int year, @AssertTrue boolean confirmed,
        java.math.BigDecimal expectedVacationDays, java.math.BigDecimal expectedSickDays,
        java.math.BigDecimal expectedBereavementDays) {}
