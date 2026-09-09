package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTimeEntryRequest {
    private Long timesheetId;
    private String entryDate;  // ISO format YYYY-MM-DD
    private String hours;
    private String notes;
    private Long projectId;
    private java.util.List<TimeEntrySessionDTO> sessions;
}
