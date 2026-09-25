package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;

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

        if (oldRole == UserRole.ADMIN && newRole != UserRole.ADMIN
                && userRepository.findByRole(UserRole.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("Cannot demote the last remaining Admin");
        }

        user.setRole(newRole);
        userRepository.save(user);

        auditService.logAction(requestingUserId, "ROLE_CHANGED", "User", userId,
                "Old role: " + oldRole + ", New role: " + newRole);
    }

    public UserDTO updateJoiningDate(Long id, java.time.LocalDate joiningDate, User requester) {
        if (requester == null || !requester.isAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only Admin can edit joining dates");
        User employee = userRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        employee.setJoiningDate(joiningDate);
        auditService.logAction(requester.getId(), "USER_PROFILE_UPDATED", "User", id, "Joining date: " + joiningDate);
        return toDTO(userRepository.save(employee));
    }

    public UserDTO updateOwnProfile(String email, UpdateProfileRequest request) {
        String timezone = request.getTimezone();
        if (timezone != null && !java.time.ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Choose a valid timezone");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isAdmin() && clean(request.getSsnLast4()) != null) {
            throw new org.springframework.security.access.AccessDeniedException("SSN is not available for Admin profiles");
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
        if (!user.isAdmin()) {
            user.setSsnLast4(clean(request.getSsnLast4()));
        }
        user.setProfileImageUrl(clean(request.getProfileImageUrl()));
        user.setPhoneNumber(clean(request.getPhoneNumber()));
        user.setPersonalEmail(clean(request.getPersonalEmail()));
        if (timezone != null) {
            user.setTimezone(timezone);
        }
        user.setAddressLine1(clean(request.getAddressLine1()));
        user.setAddressLine2(clean(request.getAddressLine2()));
        user.setCity(clean(request.getCity()));
        user.setStateProvince(clean(request.getStateProvince()));
        user.setPostalCode(clean(request.getPostalCode()));
        user.setCountry(clean(request.getCountry()));
        user.setBloodGroup(clean(request.getBloodGroup()));
        user.setEmergencyContactName(clean(request.getEmergencyContactName()));
        user.setEmergencyContactRelationship(clean(request.getEmergencyContactRelationship()));
        user.setEmergencyContactPhone(clean(request.getEmergencyContactPhone()));
        user.setEmergencyContactEmail(clean(request.getEmergencyContactEmail()));
        user.setProfileCompleted(true);

        User saved = userRepository.save(user);
        auditService.logAction(saved.getId(), "USER_PROFILE_UPDATED", "User", saved.getId(),
                "User updated their profile");
        UserDTO profile = toDTO(saved);
        profile.setPhoneNumber(saved.getPhoneNumber());
        profile.setPersonalEmail(saved.getPersonalEmail());
        profile.setAddressLine1(saved.getAddressLine1());
        profile.setAddressLine2(saved.getAddressLine2());
        profile.setCity(saved.getCity());
        profile.setStateProvince(saved.getStateProvince());
        profile.setPostalCode(saved.getPostalCode());
        profile.setCountry(saved.getCountry());
        profile.setBloodGroup(saved.getBloodGroup());
        profile.setEmergencyContactName(saved.getEmergencyContactName());
        profile.setEmergencyContactRelationship(saved.getEmergencyContactRelationship());
        profile.setEmergencyContactPhone(saved.getEmergencyContactPhone());
        profile.setEmergencyContactEmail(saved.getEmergencyContactEmail());
        return profile;
    }

    public boolean hasAdminRole(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.isAdmin();
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
                .accountStatus(user.getAccountStatus())
                .timezone(user.getTimezone())
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .jobTitle(user.getJobTitle())
                .joiningDate(user.getJoiningDate())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(user.isAdmin() ? null : user.getSsnLast4())
                .profileImageUrl(user.getProfileImageUrl())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
