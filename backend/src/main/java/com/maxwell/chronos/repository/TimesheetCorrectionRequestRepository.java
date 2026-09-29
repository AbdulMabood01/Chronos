package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimesheetCorrectionRequest;
import com.maxwell.chronos.enums.TimesheetCorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TimesheetCorrectionRequestRepository extends JpaRepository<TimesheetCorrectionRequest, Long> {
    List<TimesheetCorrectionRequest> findByTimesheetIdAndProjectIdOrderByCreatedAtDesc(Long timesheetId, Long projectId);
    List<TimesheetCorrectionRequest> findByStatusOrderByCreatedAtAsc(TimesheetCorrectionStatus status);
    boolean existsByTimesheetIdAndProjectIdAndStatus(Long timesheetId, Long projectId, TimesheetCorrectionStatus status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TimesheetCorrectionRequest r where r.id = :id")
    Optional<TimesheetCorrectionRequest> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
