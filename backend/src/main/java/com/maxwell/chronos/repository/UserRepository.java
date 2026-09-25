package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<User> findByEmail(String email);
    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.passwordResetHash = :hash")
    Optional<Long> findIdByPasswordResetHash(@org.springframework.data.repository.query.Param("hash") String hash);
    Optional<User> findByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Query("select u.id from User u where lower(u.email) = lower(:email)")
    Optional<Long> findIdByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);
    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.entraId = :subject")
    Optional<Long> findIdByEntraId(@org.springframework.data.repository.query.Param("subject") String subject);
    Optional<User> findByEntraId(String entraId);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByIsActiveTrue();
    List<User> findByRole(UserRole role);
    boolean existsByRole(UserRole role);
}
