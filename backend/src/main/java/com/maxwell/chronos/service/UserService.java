package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Value("${chronos.initialSuperAdminEmail:}")
    private String initialSuperAdminEmail;

    public UserDTO findById(Long id) {
        return userRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public UserDTO findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::toDTO)
                .orElse(null);
    }

    public User findUserEntityByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public User findUserEntityById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findOrCreateByEntraId(String entraId, String email, String firstName, String lastName) {
        Optional<User> existing = userRepository.findByEntraId(entraId);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = User.builder()
                .employeeId(generateEmployeeId())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .entraId(entraId)
                .role(isInitialSuperAdmin(email) ? UserRole.SUPER_ADMIN : UserRole.EMPLOYEE)
                .hourlyRate(BigDecimal.ZERO)
                .isActive(true)
                .profileCompleted(false)
                .build();

        User saved = userRepository.save(user);
        auditService.logAction(saved.getId(), "USER_CREATED", "User", saved.getId(), 
                "Email: " + email + ", Role: " + saved.getRole());

        return saved;
    }

    // Local-development-only: finds or creates a user by email instead of a real Entra ID.
    public User findOrCreateByEmailForDev(String email, String firstName, String lastName) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = User.builder()
                .employeeId(generateEmployeeId())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .entraId("dev:" + email)
                .role(isInitialSuperAdmin(email) ? UserRole.SUPER_ADMIN : UserRole.EMPLOYEE)
                .hourlyRate(BigDecimal.ZERO)
                .isActive(true)
                .profileCompleted(false)
                .build();

        User saved = userRepository.save(user);
        auditService.logAction(saved.getId(), "USER_CREATED", "User", saved.getId(),
                "Email: " + email + ", Role: " + saved.getRole());

        return saved;
    }

    public List<com.maxwell.chronos.dto.EmployeeDirectoryDTO> getEmployeeDirectory() {
        return userRepository.findByIsActiveTrue().stream().map(user ->
                new com.maxwell.chronos.dto.EmployeeDirectoryDTO(user.getId(), user.getEmployeeId(),
                    user.getFirstName(), user.getLastName(), user.getEmail(), user.getJobTitle(),
                    user.getRole(), user.getIsActive())).toList();
    }

    public List<UserDTO> getAllActiveUsers() {
        return userRepository.findByIsActiveTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void updateHourlyRate(Long userId, BigDecimal newRate, Long requestingUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Requesting user not found"));
        
        BigDecimal oldRate = user.getAdminOverrideHourlyRate();
        user.setAdminOverrideHourlyRate(newRate);
        user.setRateOverriddenBy(requestingUser);
        user.setRateOverriddenAt(java.time.LocalDateTime.now());
        user.setRateOverrideReason("Admin override");
        userRepository.save(user);

        auditService.logAction(requestingUserId, "HOURLY_RATE_CHANGED", "User", userId,
                "Old override rate: " + oldRate + ", New override rate: " + newRate);
    }

    public void deactivateUser(Long userId, Long requestingUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        user.setIsActive(false);
        userRepository.save(user);

        auditService.logAction(requestingUserId, "USER_DEACTIVATED", "User", userId, 
                "User: " + user.getFullName());
    }

    public void reactivateUser(Long userId, Long requestingUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        user.setIsActive(true);
        userRepository.save(user);

        auditService.logAction(requestingUserId, "USER_REACTIVATED", "User", userId,
                "User: " + user.getFullName());
    }

    public void changeRole(Long userId, UserRole newRole, Long requestingUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserRole oldRole = user.getRole();

        if (userId.equals(requestingUserId)) {
            throw new IllegalArgumentException("Users cannot change their own role");
        }

        if (oldRole == UserRole.SUPER_ADMIN && newRole != UserRole.SUPER_ADMIN
                && userRepository.findByRole(UserRole.SUPER_ADMIN).size() <= 1) {
            throw new IllegalArgumentException("Cannot demote the last remaining Super Admin");
        }

        user.setRole(newRole);
        userRepository.save(user);

        auditService.logAction(requestingUserId, "ROLE_CHANGED", "User", userId,
                "Old role: " + oldRole + ", New role: " + newRole);
    }

    public UserDTO updateOwnProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isSuperAdmin() && clean(request.getSsnLast4()) != null) {
            throw new org.springframework.security.access.AccessDeniedException("SSN is not available for SuperAdmin profiles");
        }

        String firstName = clean(request.getFirstName());
        String lastName = clean(request.getLastName());

        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        user.setJobTitle(clean(request.getJobTitle()));
        user.setDateOfBirth(request.getDateOfBirth());
        if (!user.isSuperAdmin()) {
            user.setSsnLast4(clean(request.getSsnLast4()));
        }
        user.setProfileImageUrl(clean(request.getProfileImageUrl()));
        user.setProfileCompleted(true);

        User saved = userRepository.save(user);
        auditService.logAction(saved.getId(), "USER_PROFILE_UPDATED", "User", saved.getId(),
                "User updated their profile");
        return toDTO(saved);
    }

    public boolean hasSuperAdminRole(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.isSuperAdmin();
    }

    public boolean isSuperAdminEmailMatch(String email, String configuredEmail) {
        return email != null && configuredEmail != null && email.equals(configuredEmail);
    }

    private boolean isInitialSuperAdmin(String email) {
        return isSuperAdminEmailMatch(email, initialSuperAdminEmail);
    }

    private String generateEmployeeId() {
        // Simple employee ID generation - could be more sophisticated
        long count = userRepository.count();
        return "EMP" + String.format("%06d", count + 1);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .jobTitle(user.getJobTitle())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(user.isSuperAdmin() ? null : user.getSsnLast4())
                .profileImageUrl(user.getProfileImageUrl())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .email(user.getEmail())
                .role(user.getRole())
                .hourlyRate(user.getEffectiveHourlyRate())
                .defaultHourlyRate(user.getHourlyRate())
                .adminOverrideHourlyRate(user.getAdminOverrideHourlyRate())
                .effectiveHourlyRate(user.getEffectiveHourlyRate())
                .rateOverridden(user.hasAdminRateOverride())
                .rateOverriddenByName(user.getRateOverriddenBy() != null ? user.getRateOverriddenBy().getFullName() : null)
                .rateOverriddenAt(user.getRateOverriddenAt())
                .rateOverrideReason(user.getRateOverrideReason())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
