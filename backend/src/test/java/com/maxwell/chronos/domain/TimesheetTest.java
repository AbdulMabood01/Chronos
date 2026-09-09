package com.maxwell.chronos.domain;

import com.maxwell.chronos.enums.TimesheetStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimesheetTest {
    @Test
    void submittedApprovedAndLockedTimesheetsAreReadOnly() {
        assertTrue(timesheet(TimesheetStatus.DRAFT).isEditable());
        assertTrue(timesheet(TimesheetStatus.REJECTED).isEditable());

        assertFalse(timesheet(TimesheetStatus.SUBMITTED).isEditable());
        assertFalse(timesheet(TimesheetStatus.APPROVED).isEditable());
        assertFalse(timesheet(TimesheetStatus.LOCKED).isEditable());
    }

    private Timesheet timesheet(TimesheetStatus status) {
        return Timesheet.builder()
                .status(status)
                .build();
    }
}
