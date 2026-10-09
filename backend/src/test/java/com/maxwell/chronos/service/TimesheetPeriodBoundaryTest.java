package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class TimesheetPeriodBoundaryTest {
    @Mock JdbcTemplate db;
    @Mock ProjectRepository projects;
    @Mock UserRepository users;
    @Mock TimeEntryRepository entries;
    @Mock CompanyAccessService access;
    @Mock NotificationService notifications;
    @InjectMocks TimesheetPeriodService service;
    Project project;
    @BeforeEach void setup() {
        project = Project.builder().id(1L).companyId(2L).build();
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(db.query(anyString(), any(RowMapper.class), anyLong(), any(LocalDate.class))).thenReturn(List.of());

    }
    void noChange() { when(db.query(anyString(), any(RowMapper.class), anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of()); }
    @Test void dailyLeapDayAndYearEndRemainSingleDays() {
        noChange();
        project.setApprovalFrequency("DAILY");
        for(String value:List.of("2024-02-29","2025-12-31","2026-01-01")) {
            LocalDate date=LocalDate.parse(value); var p=service.period(1,date);
            assertEquals(date,p.start()); assertEquals(date,p.end()); assertEquals("DAILY",p.frequency());
        }
    }
    @Test void weeklyMondayAndSundaySharePeriodAcrossYearAndMonth() {
        noChange();
        project.setApprovalFrequency("WEEKLY");
        for(int offset=0;offset<7;offset++) {
            var p=service.period(1,LocalDate.parse("2025-12-29").plusDays(offset));
            assertEquals(LocalDate.parse("2025-12-29"),p.start()); assertEquals(LocalDate.parse("2026-01-04"),p.end());
        }
        assertEquals(LocalDate.parse("2026-01-05"),service.period(1,LocalDate.parse("2026-01-05")).start());
    }
    @Test void monthlyLeapAndNonLeapFebruaryAndThirtyDayMonth() {
        noChange();
        project.setApprovalFrequency("MONTHLY");
        for(String value:List.of("2024-02-29","2025-02-28","2026-04-30","2026-12-31")) {
            LocalDate date=LocalDate.parse(value);var p=service.period(1,date);
            assertEquals(date.withDayOfMonth(1),p.start()); assertEquals(date,p.end());
        }
    }
    @Test void scheduledChangeCutsOldWeekAndStartsNewPeriod() {
        project.setApprovalFrequency("WEEKLY");
        LocalDate change=LocalDate.parse("2026-01-01");
        when(db.query(anyString(), any(RowMapper.class), anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(change));
        assertEquals(change.minusDays(1),service.period(1,change.minusDays(1)).end());
        assertEquals(change,service.period(1,change).start());
    }
}
