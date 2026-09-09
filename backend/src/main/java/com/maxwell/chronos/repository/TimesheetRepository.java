package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.enums.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    Optional<Timesheet> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);
    List<Timesheet> findByUserId(Long userId);
    List<Timesheet> findByStatus(TimesheetStatus status);
    List<Timesheet> findByYearAndMonth(Integer year, Integer month);
    List<Timesheet> findByYearAndMonthAndStatus(Integer year, Integer month, TimesheetStatus status);
}
