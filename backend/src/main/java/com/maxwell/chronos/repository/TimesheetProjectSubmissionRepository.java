package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.enums.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetProjectSubmissionRepository extends JpaRepository<TimesheetProjectSubmission, Long> {
    @org.springframework.data.jpa.repository.Query("select s.timesheet.id from TimesheetProjectSubmission s where s.id = :id")
    Optional<Long> findTimesheetId(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<TimesheetProjectSubmission> findByTimesheetIdAndProjectId(Long timesheetId, Long projectId);
    List<TimesheetProjectSubmission> findByProjectIdAndTimesheetUserId(Long projectId, Long userId);
    List<TimesheetProjectSubmission> findByProjectId(Long projectId);
    @org.springframework.data.jpa.repository.Query("select count(e) from TimeEntry e where e.project.id = :projectId and e.hours > 0 and not exists (select s.id from TimesheetProjectSubmission s where s.project.id = :projectId and s.timesheet.id = e.timesheet.id and s.status in :finalStatuses)")
    long countUnfinalizedEntries(@org.springframework.data.repository.query.Param("projectId") Long projectId,
            @org.springframework.data.repository.query.Param("finalStatuses") List<TimesheetStatus> finalStatuses);
    List<TimesheetProjectSubmission> findByStatus(TimesheetStatus status);
    List<TimesheetProjectSubmission> findByTimesheetUserId(Long userId);
    List<TimesheetProjectSubmission> findByTimesheetId(Long timesheetId);
}
