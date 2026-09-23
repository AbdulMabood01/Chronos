package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.EmployeeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmployeeInvitationRepository extends JpaRepository<EmployeeInvitation, Long> {
    Optional<EmployeeInvitation> findByTokenHash(String hash);
    @org.springframework.data.jpa.repository.Query("select i.employeeId from EmployeeInvitation i where i.tokenHash = :hash")
    Optional<Long> findEmployeeIdByTokenHash(@org.springframework.data.repository.query.Param("hash") String hash);
    Optional<EmployeeInvitation> findByEmployeeId(Long employeeId);
}
