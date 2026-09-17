package com.maxwell.chronos.repository;
import com.maxwell.chronos.domain.LeaveAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LeaveAllowanceRepository extends JpaRepository<LeaveAllowance, Long> {
    Optional<LeaveAllowance> findByUserIdAndYear(Long userId, int year);
}
