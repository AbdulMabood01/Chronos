package com.maxwell.chronos.dto;
import java.math.BigDecimal;
public record LeaveBalanceDTO(int year, boolean configured, Balance vacation, Balance sick, Balance bereavement) {
    public record Balance(BigDecimal allowanceDays, BigDecimal extraDays, BigDecimal usedDays,
                          BigDecimal remainingDays, BigDecimal unpaidDays) {}
}
