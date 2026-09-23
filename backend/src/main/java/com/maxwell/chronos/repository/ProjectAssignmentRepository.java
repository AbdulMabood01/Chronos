package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.ProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {
    @org.springframework.data.jpa.repository.Query("select a from ProjectAssignment a join fetch a.user join fetch a.project where a.project.id in :ids")
    List<ProjectAssignment> findForHealth(@org.springframework.data.repository.query.Param("ids") java.util.Set<Long> ids);
    @org.springframework.data.jpa.repository.Query("select a from ProjectAssignment a join fetch a.user join fetch a.project where a.user.id in :ids and a.isActive = true")
    List<ProjectAssignment> findCapacityForHealth(@org.springframework.data.repository.query.Param("ids") java.util.Set<Long> ids);
    Optional<ProjectAssignment> findByProjectIdAndUserId(Long projectId, Long userId);
    boolean existsByProjectIdAndUserIdAndIsActiveTrue(Long projectId, Long userId);
    List<ProjectAssignment> findByProjectId(Long projectId);
    List<ProjectAssignment> findByProjectIdAndIsActiveTrue(Long projectId);
    List<ProjectAssignment> findByUserId(Long userId);
    List<ProjectAssignment> findByUserIdAndIsActiveTrue(Long userId);
}
