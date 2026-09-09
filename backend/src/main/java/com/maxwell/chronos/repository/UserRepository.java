package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEntraId(String entraId);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByIsActiveTrue();
    List<User> findByRole(UserRole role);
    boolean existsByRole(UserRole role);
}
