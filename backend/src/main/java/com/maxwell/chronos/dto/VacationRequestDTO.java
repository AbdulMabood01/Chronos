package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.enums.VacationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRequestDTO {
    private Long id;
    private Long userId;
    private String userName;
    private LocalDate startDate;
    private LocalDate endDate;
    private VacationType vacationType;
    private BigDecimal hours;
    private VacationStatus status;
    private String notes;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private LocalDateTime rejectedAt;
    private String rejectedByName;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
