package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.hours), 0) from TimeEntry e where e.project.id = :projectId")
    java.math.BigDecimal totalRecordedHours(@org.springframework.data.repository.query.Param("projectId") Long projectId);
    boolean existsByProjectManagerIdOrProjectManagerHoursApproverId(Long managerId, Long approverId);
    boolean existsByProjectManagerId(Long managerId);
    Optional<Project> findByCodeIgnoreCase(String code);
    List<Project> findByIsActiveTrueOrderByCodeAsc();
    List<Project> findByProjectManagerIdAndIsActiveTrue(Long managerId);
    List<Project> findByProjectManagerIdAndStatus(Long managerId, ProjectStatus status);
}
