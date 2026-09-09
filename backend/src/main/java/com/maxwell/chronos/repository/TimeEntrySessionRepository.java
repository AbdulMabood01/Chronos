package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.TimeEntrySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeEntrySessionRepository extends JpaRepository<TimeEntrySession, Long> {
}
