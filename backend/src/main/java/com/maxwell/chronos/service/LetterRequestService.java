package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.CreateLetterRequest;
import com.maxwell.chronos.dto.LetterRequestDTO;
import com.maxwell.chronos.enums.LetterRequestType;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.repository.LetterRequestRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class LetterRequestService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final String EMPLOYER_INFORMATION = """
            Employer Information
            E-Verification Number: 2601516
            EIN: 933751606
            Employer Telephone Number: (872) 999-2576
            Supervisor: Syed Hussain
            Email: hr@maxwellnetwork.org
            """;
    private static final String COMPANY_FOOTER = """
            tech.maxwellnetwork.org
            5875 N Lincoln Ave, Suite LL26, Chicago, IL 60659-2122
            """;

    private final LetterRequestRepository letterRequestRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public LetterRequestDTO createLetterRequest(Long userId, CreateLetterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        validateRequest(request);

        LetterRequest letterRequest = LetterRequest.builder()
                .user(user)
                .requestType(request.getRequestType())
                .status(VacationStatus.SUBMITTED)
                .recipientOrganization(trim(request.getRecipientOrganization()))
                .recipientAddress(trim(request.getRecipientAddress()))
                .purpose(trim(request.getPurpose()))
                .requestedFullName(trim(request.getRequestedFullName()))
                .requestedJobTitle(trim(request.getRequestedJobTitle()))
                .employmentStartDate(request.getEmploymentStartDate())
                .immigrationCaseType(trim(request.getImmigrationCaseType()))
                .destinationCountry(trim(request.getDestinationCountry()))
                .consulateName(trim(request.getConsulateName()))
                .effectiveDate(request.getEffectiveDate())
                .travelStartDate(request.getTravelStartDate())
                .travelEndDate(request.getTravelEndDate())
                .vacationStartDate(request.getVacationStartDate())
                .vacationEndDate(request.getVacationEndDate())
                .notes(trim(request.getNotes()))
                .submittedAt(LocalDateTime.now())
                .build();

        LetterRequest saved = letterRequestRepository.save(letterRequest);
        auditService.logAction(userId, "LETTER_REQUEST_SUBMITTED", "LetterRequest", saved.getId(),
                "Type: " + saved.getRequestType());
        notifyAdmins(saved);

        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LetterRequestDTO> getMyRequests(Long userId) {
        return letterRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LetterRequestDTO> getPendingRequests() {
        return letterRequestRepository.findByStatusOrderBySubmittedAtDesc(VacationStatus.SUBMITTED).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LetterRequestDTO getRequest(Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = letterRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Letter request not found"));
        if (!isAdmin && !request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to view this letter request");
        }
        return toDTO(request);
    }

    public LetterRequestDTO approveLetterRequest(Long requestId, Long adminId) {
        User admin = requireAdmin(adminId);
        LetterRequest request = requireSubmittedRequest(requestId);

        request.setStatus(VacationStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedBy(admin);
        LetterRequest saved = letterRequestRepository.save(request);

        auditService.logAction(adminId, "LETTER_REQUEST_APPROVED", "LetterRequest", requestId,
                "Employee: " + saved.getUser().getFullName());
        notificationService.createNotification(saved.getUser().getId(),
                "LETTER_REQUEST_APPROVED",
                "Letter Request Approved",
                "Your " + displayType(saved.getRequestType()) + " is approved and ready to download.",
                saved.getId(),
                "LetterRequest");

        return toDTO(saved);
    }

    public LetterRequestDTO rejectLetterRequest(Long requestId, String rejectionReason, Long adminId) {
        User admin = requireAdmin(adminId);
        LetterRequest request = requireSubmittedRequest(requestId);

        String reason = trim(rejectionReason);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        request.setStatus(VacationStatus.REJECTED);
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectedBy(admin);
        request.setRejectionReason(reason);
        LetterRequest saved = letterRequestRepository.save(request);

        auditService.logAction(adminId, "LETTER_REQUEST_REJECTED", "LetterRequest", requestId,
                "Reason: " + reason);
        notificationService.createNotification(saved.getUser().getId(),
                "LETTER_REQUEST_REJECTED",
                "Letter Request Rejected",
                "Your " + displayType(saved.getRequestType()) + " was rejected. Reason: " + reason,
                saved.getId(),
                "LetterRequest");

        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = letterRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Letter request not found"));

        if (!isAdmin && !request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to download this letter");
        }
        if (!isAdmin && !VacationStatus.APPROVED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Letter is not approved yet");
        }

        return LetterPdf.render(displayType(request.getRequestType()), "LTR-" + request.getId(),
                VacationStatus.APPROVED.equals(request.getStatus()), buildLetterText(request));
    }

    @Transactional(readOnly = true)
    public String buildFilename(Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = letterRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Letter request not found"));
        if (!isAdmin && !request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to download this letter");
        }

        String name = request.getUser().getFullName().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isBlank()) {
            name = "employee_" + request.getUser().getId();
        }
        return displayType(request.getRequestType()).replaceAll("[^A-Za-z0-9]+", "_") + "_" + name + ".pdf";
    }

    private void validateRequest(CreateLetterRequest request) {
        if (request == null || request.getRequestType() == null) {
            throw new IllegalArgumentException("Letter request type is required");
        }
        if (isBlank(request.getRequestedFullName()) || isBlank(request.getRequestedJobTitle())
                || request.getEmploymentStartDate() == null) {
            throw new IllegalArgumentException("Full name, title, and job start date are required");
        }
        if (request.getRequestType() == LetterRequestType.TRAVEL) {
            if (request.getTravelStartDate() == null || request.getTravelEndDate() == null) {
                throw new IllegalArgumentException("Travel dates are required");
            }
            if (request.getTravelStartDate().isAfter(request.getTravelEndDate())) {
                throw new IllegalArgumentException("Travel start date cannot be after end date");
            }
        }
        if (request.getRequestType() == LetterRequestType.VACATION) {
            if (request.getVacationStartDate() == null || request.getVacationEndDate() == null) {
                throw new IllegalArgumentException("Vacation letter dates are required");
            }
            if (request.getVacationStartDate().isAfter(request.getVacationEndDate())) {
                throw new IllegalArgumentException("Vacation start date cannot be after end date");
            }
        }
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        if (!admin.isSuperAdmin()) {
            throw new IllegalArgumentException("Only Admin can approve letter requests");
        }
        return admin;
    }

    private LetterRequest requireSubmittedRequest(Long requestId) {
        LetterRequest request = letterRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Letter request not found"));
        if (!VacationStatus.SUBMITTED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Only submitted letter requests can be reviewed");
        }
        return request;
    }

    private void notifyAdmins(LetterRequest request) {
        List<User> admins = new ArrayList<>();
        admins.addAll(userRepository.findByRole(UserRole.SUPER_ADMIN));
        for (User admin : admins) {
            notificationService.createNotification(admin.getId(),
                    "LETTER_REQUEST_SUBMITTED",
                    "Letter Request Submitted",
                    request.getUser().getFullName() + " requested a " + displayType(request.getRequestType()) + ".",
                    request.getId(),
                    "LetterRequest");
        }
    }

    private LetterRequestDTO toDTO(LetterRequest request) {
        return LetterRequestDTO.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userName(request.getUser().getFullName())
                .employeeId(request.getUser().getEmployeeId())
                .email(request.getUser().getEmail())
                .jobTitle(request.getUser().getJobTitle())
                .requestType(request.getRequestType())
                .status(request.getStatus())
                .recipientOrganization(request.getRecipientOrganization())
                .recipientAddress(request.getRecipientAddress())
                .purpose(request.getPurpose())
                .requestedFullName(request.getRequestedFullName())
                .requestedJobTitle(request.getRequestedJobTitle())
                .employmentStartDate(request.getEmploymentStartDate())
                .immigrationCaseType(request.getImmigrationCaseType())
                .destinationCountry(request.getDestinationCountry())
                .consulateName(request.getConsulateName())
                .effectiveDate(request.getEffectiveDate())
                .travelStartDate(request.getTravelStartDate())
                .travelEndDate(request.getTravelEndDate())
                .vacationStartDate(request.getVacationStartDate())
                .vacationEndDate(request.getVacationEndDate())
                .notes(request.getNotes())
                .letterPreview(buildLetterText(request))
                .submittedAt(request.getSubmittedAt())
                .approvedAt(request.getApprovedAt())
                .approvedByName(request.getApprovedBy() != null ? request.getApprovedBy().getFullName() : null)
                .rejectedAt(request.getRejectedAt())
                .rejectedByName(request.getRejectedBy() != null ? request.getRejectedBy().getFullName() : null)
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private String buildLetterText(LetterRequest request) {
        User user = request.getUser();
        String date = formatDate(LocalDate.now());
        String fullName = valueOrDefault(request.getRequestedFullName(), user.getFullName());
        String jobTitle = valueOrDefault(request.getRequestedJobTitle(), valueOrDefault(user.getJobTitle(), "employee"));
        String employmentStart = formatDate(request.getEmploymentStartDate());
        String company = "Maxwell Network Inc";

        String body = switch (request.getRequestType()) {
            case EMPLOYMENT_VERIFICATION -> String.join("\n\n",
                    String.format("This letter is issued at the request of %s for employment verification purposes.", fullName),
                    String.format("This is to confirm that %s is currently employed with %s. %s holds the position of %s and has been employed with the company since %s.", fullName, company, fullName, jobTitle, employmentStart),
                    "This confirmation is based on the information available in the company's employment records as of the date of this letter. The employee remains in active status with the organization.",
                    "Please accept this letter as official confirmation of current employment. Any additional verification may be directed to the Human Resources department at Maxwell Network Inc.");
            case TRAVEL -> String.join("\n\n",
                    String.format("This letter is issued at the request of %s in connection with planned travel.", fullName),
                    String.format("This is to confirm that %s is currently employed with %s as %s and has been employed with the company since %s.", fullName, company, jobTitle, employmentStart),
                    String.format("Based on the information submitted for review, %s plans to travel%s from %s to %s. The company has no restriction on this travel during the stated period, provided all applicable company policies and work obligations are satisfied.", fullName, travelDestinationText(request), formatDate(request.getTravelStartDate()), formatDate(request.getTravelEndDate())),
                    String.format("%s is expected to continue employment with %s following the travel period. This letter is provided for presentation to the appropriate requesting authority, airline, consulate, border official, or other concerned party.", fullName, company));
            case VACATION -> String.join("\n\n",
                    String.format("This letter is issued at the request of %s for vacation confirmation purposes.", fullName),
                    String.format("This is to confirm that %s is currently employed with %s as %s and has been employed with the company since %s.", fullName, company, jobTitle, employmentStart),
                    String.format("%s has requested vacation leave from %s through %s. The request has been submitted through the company's administrative workflow and is subject to the final approval status recorded in Chronos.", fullName, formatDate(request.getVacationStartDate()), formatDate(request.getVacationEndDate())),
                    String.format("This letter may be used to confirm the employee's current employment status and the vacation dates submitted for administrative review. %s is expected to resume regular work responsibilities after the approved vacation period.", fullName));
        };

        List<String> sections = new ArrayList<>();
        sections.add(company);
        sections.add(date);
        sections.add("");
        sections.add("To whomsoever it may be concerned.");
        sections.add("");
        sections.add("Subject: " + displayType(request.getRequestType()));
        sections.add("");
        sections.add(body);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            sections.add("");
            sections.add("Additional notes: " + request.getNotes());
        }
        sections.add("");
        sections.add("Best Regards,");
        sections.add("Syed Hussain");
        sections.add("Manager");
        sections.add("");
        sections.add(EMPLOYER_INFORMATION.strip());
        sections.add("");
        sections.add(COMPANY_FOOTER.strip());
        return String.join("\n", sections);
    }

    private String displayType(LetterRequestType type) {
        return switch (type) {
            case EMPLOYMENT_VERIFICATION -> "Employment Verification Letter";
            case TRAVEL -> "Travel Letter";
            case VACATION -> "Vacation Letter";
        };
    }

    private String travelDestinationText(LetterRequest request) {
        String destination = request.getDestinationCountry();
        return destination != null && !destination.isBlank() ? " to " + destination : "";
    }

    private String formatDate(LocalDate date) {
        return date != null ? DATE_FORMAT.format(date) : "the requested date";
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String trim(String value) {
        return value != null ? value.trim() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
