package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.enums.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Timesheet t where t.id = :id")
    Optional<Timesheet> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Timesheet t where t.user.id = :userId and t.year = :year and t.month = :month")
    Optional<Timesheet> findPeriodForUpdate(@org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("year") Integer year,
            @org.springframework.data.repository.query.Param("month") Integer month);
    Optional<Timesheet> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);
    List<Timesheet> findByUserId(Long userId);
    List<Timesheet> findByStatus(TimesheetStatus status);
    List<Timesheet> findByYearAndMonth(Integer year, Integer month);
    List<Timesheet> findByYearAndMonthAndStatus(Integer year, Integer month, TimesheetStatus status);
}
