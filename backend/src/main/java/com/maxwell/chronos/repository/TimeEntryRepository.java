package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    Optional<TimeEntry> findByTimesheetIdAndEntryDate(Long timesheetId, LocalDate entryDate);
    Optional<TimeEntry> findByTimesheetIdAndEntryDateAndProjectId(Long timesheetId, LocalDate entryDate, Long projectId);
    List<TimeEntry> findByTimesheetId(Long timesheetId);
}
