package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.UpdateProfileRequest;
import com.maxwell.chronos.dto.UserDTO;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthSessionService sessions;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    public UserService(UserRepository userRepository, AuditService auditService, AuthSessionService sessions,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    // Retains the constructor used by service-level fixtures.
    UserService(UserRepository userRepository, AuditService auditService, AuthSessionService sessions) {
        this(userRepository, auditService, sessions, null);
    }

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
                new com.maxwell.chronos.dto.EmployeeDirectoryDTO(user.getId(), null,
                    user.getFirstName(), user.getLastName(), user.getEmail(), null,
                    null, user.getIsActive())).toList();
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
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if(jdbc!=null)new CompanyAccessService(jdbc).guardAccountAdminAccessLoss(userId);
        
        user.setIsActive(false);
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        sessions.revokeAll(userId);
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

    public UserDTO lockAccount(Long id, Long actorId, String reason) {
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null || !isPlatform(actor.getId())) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        if (id.equals(actorId)) throw new IllegalArgumentException("You cannot lock your own account");
        User target = userRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if(jdbc!=null)new CompanyAccessService(jdbc).guardAccountAdminAccessLoss(id);
        target.setAdminLocked(true);
        target.setAdminLockReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)));
        target.setLockedAt(java.time.Instant.now());
        target.setCredentialVersion(target.getCredentialVersion() + 1);
        sessions.revokeAll(id);
        auditService.logSecurityAction(actorId, com.maxwell.chronos.enums.AuditAction.ACCOUNT_LOCKED, id);
        return toDTO(target);
    }

    public UserDTO unlockAccount(Long id, Long actorId) {
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null || !isPlatform(actor.getId())) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        User target = userRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setAdminLocked(false);
        target.setAdminLockReason(null);
        target.setLockedUntil(null);
        target.setFailedLoginCount(0);
        target.setUnlockedBy(actorId);
        target.setUnlockedAt(java.time.Instant.now());
        auditService.logSecurityAction(actorId, com.maxwell.chronos.enums.AuditAction.ACCOUNT_UNLOCKED, id);
        return toDTO(target);
    }

    public void signOutAll(Long id, Long actorId) {
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null || !isPlatform(actor.getId())) throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        User target = userRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setCredentialVersion(target.getCredentialVersion() + 1);
        sessions.revokeAll(id);
        auditService.logSecurityAction(actorId, com.maxwell.chronos.enums.AuditAction.SESSIONS_REVOKED, id);
    }

    public void changeRole(Long userId,UserRole role,Long actor){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Use scoped company/project roles or platform administrators");}

    public UserDTO updateJoiningDate(Long id, java.time.LocalDate joiningDate, User requester) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "Edit joining dates within a company membership instead");
    }

    public UserDTO updateOwnProfile(String email, UpdateProfileRequest request) {
        if(request.getJobTitle()!=null) throw new IllegalArgumentException("Job title is managed in your company employment details");
        String timezone = request.getTimezone();
        if (timezone != null && !java.time.ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Choose a valid timezone");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user=userRepository.findForUpdate(user.getId()).orElseThrow(()->new IllegalArgumentException("User not found"));
        if (isPlatform(user.getId()) && clean(request.getSsnLast4()) != null) {
            throw new org.springframework.security.access.AccessDeniedException("SSN is not available for Admin profiles");
        }

        String firstName = clean(request.getFirstName());
        String lastName = clean(request.getLastName());
        if(!user.isProfileCorrectionOpen() && Boolean.TRUE.equals(user.getProfileCompleted()) &&
            (!java.util.Objects.equals(firstName,user.getFirstName()) || !java.util.Objects.equals(lastName,user.getLastName())
            || !java.util.Objects.equals(request.getDateOfBirth(),user.getDateOfBirth())))
            throw new IllegalArgumentException("Name and date of birth are locked after profile submission");

        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        user.setDateOfBirth(request.getDateOfBirth());
        if(!user.isProfileCorrectionOpen() && Boolean.TRUE.equals(user.getProfileDetailsSubmitted()) &&
            ((clean(user.getGender()) != null && !java.util.Objects.equals(clean(request.getGender()),user.getGender()))
             || (clean(user.getRace()) != null && !java.util.Objects.equals(clean(request.getRace()),user.getRace()))
             || (clean(user.getEthnicity()) != null && !java.util.Objects.equals(clean(request.getEthnicity()),user.getEthnicity()))
             || (user.getJoiningDate() != null && !java.util.Objects.equals(request.getJoiningDate(),user.getJoiningDate()))))
            throw new IllegalArgumentException("Gender, race, ethnicity and joining date are locked after submission");
        validateProfileChoice("Gender", request.getGender(), user.getGender(), java.util.Set.of("Male", "Female", "Other"));
        validateProfileChoice("Race", request.getRace(), user.getRace(), java.util.Set.of("American Indian or Alaska Native", "Asian", "Black or African American", "Native Hawaiian or Other Pacific Islander", "White", "Two or more races", "Other", "Prefer not to say"));
        validateProfileChoice("Ethnicity", request.getEthnicity(), user.getEthnicity(), java.util.Set.of("Hispanic or Latino", "Not Hispanic or Latino", "Other", "Prefer not to say"));
        if(!user.isProfileCorrectionOpen() && Boolean.TRUE.equals(user.getProfileDetailsSubmitted()) && clean(user.getBloodGroup())!=null && !java.util.Objects.equals(clean(request.getBloodGroup()),user.getBloodGroup()))
            throw new IllegalArgumentException("Blood group is locked after submission");
        if(!user.isProfileCorrectionOpen() && Boolean.TRUE.equals(user.getProfileCompleted()) && clean(user.getProfileImageUrl())!=null && !java.util.Objects.equals(clean(request.getProfileImageUrl()),user.getProfileImageUrl()))
            throw new IllegalArgumentException("Profile photo is locked after submission");
        boolean corrected=user.isProfileCorrectionOpen();
        user.setProfileCorrectionOpen(false);
        user.setGender(clean(request.getGender()));user.setRace(clean(request.getRace()));user.setEthnicity(clean(request.getEthnicity()));user.setJoiningDate(request.getJoiningDate());
        user.setProfileDetailsSubmitted(true);
        if (!isPlatform(user.getId()) && request.getSsnLast4() != null) {
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
        if(corrected && jdbc!=null) jdbc.update("UPDATE member_detail_corrections SET status='COMPLETED',completed_at=now() WHERE target_user_id=? AND kind='PROFILE' AND status='APPROVED'",user.getId());
        auditService.logAction(saved.getId(), "USER_PROFILE_UPDATED", "User", saved.getId(),
                "User updated their profile");
        UserDTO profile = toDTO(saved);
        profile.setGender(saved.getGender());profile.setRace(saved.getRace());profile.setEthnicity(saved.getEthnicity());profile.setJoiningDate(saved.getJoiningDate());profile.setProfileDetailsSubmitted(saved.getProfileDetailsSubmitted());
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
        return user != null && isPlatform(user.getId());
    }

    private boolean isPlatform(Long id){return jdbc!=null && new CompanyAccessService(jdbc).hasPlatformRole(id,"PLATFORM_ADMIN");}

    private void validateProfileChoice(String label, String value, String existing, java.util.Set<String> choices) {
        String normalized = clean(value);
        if (normalized != null && !choices.contains(normalized) && !java.util.Objects.equals(normalized, existing))
            throw new IllegalArgumentException("Choose a valid " + label.toLowerCase(java.util.Locale.ROOT));
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
                .adminLocked(user.isAdminLocked())
                .lockedUntil(user.getLockedUntil())
                .accountStatus(user.getAccountStatus())
                .timezone(user.getTimezone())
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .ssnLast4(isPlatform(user.getId()) ? null : user.getSsnLast4())
                .profileImageUrl(user.getProfileImageUrl())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted())).profileCorrectionOpen(user.isProfileCorrectionOpen())
                .email(user.getEmail())
                .platformAdmin(isPlatform(user.getId()))
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
