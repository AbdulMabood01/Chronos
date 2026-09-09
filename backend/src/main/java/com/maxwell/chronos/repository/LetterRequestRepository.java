package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.enums.VacationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LetterRequestRepository extends JpaRepository<LetterRequest, Long> {
    List<LetterRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<LetterRequest> findByStatusOrderBySubmittedAtDesc(VacationStatus status);
}
