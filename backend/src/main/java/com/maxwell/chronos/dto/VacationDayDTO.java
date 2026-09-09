package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.enums.VacationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationDayDTO {
    private Long vacationRequestId;
    private LocalDate date;
    private VacationType vacationType;
    private VacationStatus status;
}
