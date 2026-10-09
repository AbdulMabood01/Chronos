package com.maxwell.chronos.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class FederalHolidaysTest {
    @Test void observedHolidaysIncludeAdjacentYear() {
        assertTrue(FederalHolidays.dates(2026).contains(LocalDate.parse("2026-07-03")));
        assertTrue(FederalHolidays.dates(2021).contains(LocalDate.parse("2021-12-31")));
        assertTrue(FederalHolidays.dates(2026).contains(LocalDate.parse("2026-11-26")));
    }
    @Test void weekdaysExcludeFederalHolidaysAcrossYears() {
        assertEquals(1,FederalHolidays.workingDays(LocalDate.parse("2026-10-09"),LocalDate.parse("2026-10-12")));
        assertEquals(6,FederalHolidays.workingDays(LocalDate.parse("2026-12-24"),LocalDate.parse("2027-01-04")));
    }
}
