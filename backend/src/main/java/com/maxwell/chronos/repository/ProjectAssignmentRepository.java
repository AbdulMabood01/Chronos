package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.ProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {
    Optional<ProjectAssignment> findByProjectIdAndUserId(Long projectId, Long userId);
    boolean existsByProjectIdAndUserIdAndIsActiveTrue(Long projectId, Long userId);
    List<ProjectAssignment> findByProjectId(Long projectId);
    List<ProjectAssignment> findByProjectIdAndIsActiveTrue(Long projectId);
    List<ProjectAssignment> findByUserIdAndIsActiveTrue(Long userId);
}
