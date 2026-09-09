package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimesheetProjectSubmission;
import com.maxwell.chronos.enums.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetProjectSubmissionRepository extends JpaRepository<TimesheetProjectSubmission, Long> {
    Optional<TimesheetProjectSubmission> findByTimesheetIdAndProjectId(Long timesheetId, Long projectId);
    List<TimesheetProjectSubmission> findByStatus(TimesheetStatus status);
    List<TimesheetProjectSubmission> findByTimesheetUserId(Long userId);
    List<TimesheetProjectSubmission> findByTimesheetId(Long timesheetId);
}
