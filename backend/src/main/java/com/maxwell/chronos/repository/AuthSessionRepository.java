package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.AuthSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("now") java.time.Instant now);
}
