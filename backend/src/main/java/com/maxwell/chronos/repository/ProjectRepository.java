package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByCodeIgnoreCase(String code);
    List<Project> findByIsActiveTrueOrderByCodeAsc();
    List<Project> findByProjectManagerIdAndIsActiveTrue(Long managerId);
    List<Project> findByProjectManagerIdAndStatus(Long managerId, ProjectStatus status);
}
