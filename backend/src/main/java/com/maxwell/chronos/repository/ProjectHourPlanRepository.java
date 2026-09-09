package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.ProjectHourPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectHourPlanRepository extends JpaRepository<ProjectHourPlan, Long> {
    Optional<ProjectHourPlan> findByProjectIdAndUserIdAndYearAndMonth(Long projectId, Long userId, Integer year, Integer month);
    List<ProjectHourPlan> findByProjectIdAndYearAndMonth(Long projectId, Integer year, Integer month);
}
