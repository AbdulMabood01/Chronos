package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.hours), 0) from TimeEntry e where e.timesheet.user.id = :userId and e.project.id = :projectId and e.entryDate <= :throughDate")
    java.math.BigDecimal sumLoggedHoursToDate(@org.springframework.data.repository.query.Param("userId") Long userId,
        @org.springframework.data.repository.query.Param("projectId") Long projectId,
        @org.springframework.data.repository.query.Param("throughDate") LocalDate throughDate);
    Optional<TimeEntry> findByTimesheetIdAndEntryDate(Long timesheetId, LocalDate entryDate);
    Optional<TimeEntry> findByTimesheetIdAndEntryDateAndProjectId(Long timesheetId, LocalDate entryDate, Long projectId);
    List<TimeEntry> findByTimesheetId(Long timesheetId);
}
