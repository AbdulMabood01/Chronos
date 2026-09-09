package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.VacationRequest;
import com.maxwell.chronos.enums.VacationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {
    List<VacationRequest> findByUserId(Long userId);
    List<VacationRequest> findByStatus(VacationStatus status);
    List<VacationRequest> findByUserIdAndStatus(Long userId, VacationStatus status);
    List<VacationRequest> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    List<VacationRequest> findByUserIdAndStartDateGreaterThanEqual(Long userId, LocalDate date);
    List<VacationRequest> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId, LocalDate endDate, LocalDate startDate);
    List<VacationRequest> findByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId, VacationStatus status, LocalDate endDate, LocalDate startDate);
}
