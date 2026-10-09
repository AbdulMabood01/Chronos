package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.CreateLetterRequest;
import com.maxwell.chronos.dto.LetterRequestDTO;
import com.maxwell.chronos.dto.LetterReviewRequest;
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
public class CompanyLetterRequestService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private final LetterRequestRepository letterRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CompanyWorkflowAccess flow;
    private final CompanyAccessService access;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final CompanyLetterTemplates templates;

    public void freezeHistoricalLetters() {
        for(long id:db.queryForList("SELECT id FROM letter_requests WHERE status='APPROVED' AND company_id IS NOT NULL AND issued_pdf IS NULL FOR UPDATE",Long.class)) {
            var r=letterRequestRepository.findById(id).orElseThrow();
            String text=buildLetterText(r);
            r.setIssuedText(text);
            r.setIssuedPdf(CompanyLetterPdf.render(access.companyDisplayName(r.getCompanyId()),displayType(r.getRequestType()),"LTR-"+id,true,text));
            letterRequestRepository.save(r);
        }
    }
    public byte[] previewReview(long company,long id,long actor,LetterReviewRequest review,long version) {
        requireAdmin(company,actor);var r=requireSubmittedRequest(company,id,version);
        if(r.getUser().getId().equals(actor))throw new IllegalArgumentException("You cannot review your own letter request");
        if(review==null||isBlank(review.fullName())||isBlank(review.jobTitle())||review.employmentStartDate()==null||review.fullName().length()>200||review.jobTitle().length()>120||review.employmentStartDate().isAfter(LocalDate.now()))throw new IllegalArgumentException("Confirm a valid name, title, and employment start date.");
        var definition=templates.resolve(company,r.getRequestType(),review);
        return CompanyLetterPdf.render(definition.name(),displayType(r.getRequestType()),"LTR-"+id,false,templates.render(definition,r,review,LocalDate.now()),definition);
    }

    public LetterRequestDTO createLetterRequest(Long companyId, Long userId, CreateLetterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        flow.lockMember(companyId,user.getEmail(),false);
        access.requireEmployeeWork(companyId,userId);
        request.setRequestedFullName((user.getFirstName()+" "+user.getLastName()).trim());
        validateRequest(request);
        templates.resolve(companyId,request.getRequestType(),null);

        LetterRequest letterRequest = LetterRequest.builder()
                .companyId(companyId)
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

        LetterRequest saved = letterRequestRepository.saveAndFlush(letterRequest);
        flow.activity(companyId,userId,"LETTER_REQUEST_SUBMITTED","LetterRequest",saved.getId());
        notifyAdmins(saved);

        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LetterRequestDTO> getMyRequests(Long companyId, Long userId) {
        access.requireActiveCompanyAccess(companyId,userId);
        return letterRequestRepository.findByCompanyIdAndUserIdOrderByCreatedAtDesc(companyId,userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LetterRequestDTO> getPendingRequests(Long companyId,Long actor) {
        access.requireCompanyCapability(companyId,actor,"canReviewLeaveAndLetters");
        return letterRequestRepository.findByCompanyIdAndStatusOrderBySubmittedAtDesc(companyId,VacationStatus.SUBMITTED).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LetterRequestDTO getRequest(Long companyId, Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = scoped(companyId,requestId);
        access.requireActiveCompanyAccess(companyId,userId);
        isAdmin=access.hasCompanyRole(companyId,userId,"COMPANY_ADMIN");
        if (!isAdmin && !request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to view this letter request");
        }
        return toDTO(request);
    }

    public LetterRequestDTO approveLetterRequest(Long companyId, Long requestId, Long adminId, LetterReviewRequest review,long version) {
        User admin = requireAdmin(companyId,adminId);
        LetterRequest request = requireSubmittedRequest(companyId,requestId,version);
        if (request.getUser().getId().equals(adminId))
            throw new IllegalArgumentException("You cannot approve your own letter request");
        if (review == null || isBlank(review.fullName()) || isBlank(review.jobTitle())
                || review.employmentStartDate() == null)
            throw new IllegalArgumentException("Confirm the name, title, and employment start date before approval");
        String fullName = trim(review.fullName());
        String jobTitle = trim(review.jobTitle());
        String note = trim(review.reviewNote());
        if (fullName.length() > 200 || jobTitle.length() > 120 || (note != null && note.length() > 500))
            throw new IllegalArgumentException("Reviewed letter details are too long");
        if (review.employmentStartDate().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Employment start date cannot be in the future");
        if ((!fullName.equals(trim(request.getRequestedFullName()))
                || !jobTitle.equals(trim(request.getRequestedJobTitle()))
                || !review.employmentStartDate().equals(request.getEmploymentStartDate()))
                && isBlank(note))
            throw new IllegalArgumentException("Explain corrections to the employee's requested details");

        var definition=templates.resolve(companyId,request.getRequestType(),review);
        request.setApprovedFullName(fullName);
        request.setApprovedJobTitle(jobTitle);
        request.setApprovedEmploymentStartDate(review.employmentStartDate());
        request.setReviewNote(note);
        request.setStatus(VacationStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedBy(admin);
        String issuedText=templates.render(definition,request,review,request.getApprovedAt().toLocalDate());
        request.setIssuedDefinition(templates.serialize(definition));
        request.setIssuedText(issuedText);
        request.setIssuedPdf(CompanyLetterPdf.render(definition.name(),displayType(request.getRequestType()),"LTR-"+requestId,true,issuedText,definition));
        LetterRequest saved = letterRequestRepository.saveAndFlush(request);

        flow.activity(companyId,adminId,"LETTER_REQUEST_APPROVED","LetterRequest",requestId);
        notificationService.createNotification(saved.getUser().getId(),
                "LETTER_REQUEST_APPROVED",
                "Letter Request Approved",
                "Your " + displayType(saved.getRequestType()) + " is approved and ready to download.",
                saved.getId(),
                "LetterRequest");

        return toDTO(saved);
    }

    public LetterRequestDTO rejectLetterRequest(Long companyId, Long requestId, String rejectionReason, Long adminId,long version) {
        User admin = requireAdmin(companyId,adminId);
        LetterRequest request = requireSubmittedRequest(companyId,requestId,version);
        if (request.getUser().getId().equals(adminId))
            throw new IllegalArgumentException("You cannot reject your own letter request");

        String reason = trim(rejectionReason);
        if(reason!=null && reason.length()>500)throw new IllegalArgumentException("Rejection reason is too long");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        request.setStatus(VacationStatus.REJECTED);
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectedBy(admin);
        request.setRejectionReason(reason);
        LetterRequest saved = letterRequestRepository.saveAndFlush(request);

        flow.activity(companyId,adminId,"LETTER_REQUEST_REJECTED","LetterRequest",requestId);
        notificationService.createNotification(saved.getUser().getId(),
                "LETTER_REQUEST_REJECTED",
                "Letter Request Rejected",
                "Your " + displayType(saved.getRequestType()) + " was rejected. Reason: " + reason,
                saved.getId(),
                "LetterRequest");

        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long companyId, Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = scoped(companyId,requestId);

        access.requireActiveCompanyAccess(companyId,userId);
        isAdmin=access.hasCompanyRole(companyId,userId,"COMPANY_ADMIN");
        if (!isAdmin && !request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to download this letter");
        }
        access.requireActiveCompanyAccess(companyId,userId);
        isAdmin=access.hasCompanyRole(companyId,userId,"COMPANY_ADMIN");
        if(request.getIssuedPdf()!=null)return request.getIssuedPdf();
        if(request.getStatus()!=VacationStatus.APPROVED){
            var definition=templates.resolve(companyId,request.getRequestType(),null);
            if(!isAdmin)definition=templates.unsigned(definition);
            return CompanyLetterPdf.render(definition.name(),displayType(request.getRequestType()),"LTR-"+requestId,false,templates.render(definition,request,null,LocalDate.now()),definition);
        }
        return CompanyLetterPdf.render(access.companyDisplayName(companyId),displayType(request.getRequestType()), "LTR-" + request.getId(),
                VacationStatus.APPROVED.equals(request.getStatus()), buildLetterText(request));
    }

    @Transactional(readOnly = true)
    public String buildFilename(Long companyId, Long requestId, Long userId, boolean isAdmin) {
        LetterRequest request = scoped(companyId,requestId);
        access.requireActiveCompanyAccess(companyId,userId);
        isAdmin=access.hasCompanyRole(companyId,userId,"COMPANY_ADMIN");
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
        if(request!=null){
            String[] values={request.getRequestedFullName(),request.getRequestedJobTitle(),request.getRecipientOrganization(),request.getRecipientAddress(),request.getPurpose(),request.getImmigrationCaseType(),request.getDestinationCountry(),request.getConsulateName(),request.getNotes()};
            int[] limits={200,120,255,500,255,120,120,255,1000};
            for(int i=0;i<values.length;i++)if(values[i]!=null && values[i].length()>limits[i])throw new IllegalArgumentException("Letter details are too long");
            if(request.getEmploymentStartDate()!=null && request.getEmploymentStartDate().isAfter(LocalDate.now()))throw new IllegalArgumentException("Employment start date cannot be in the future");
        }
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

    private User requireAdmin(Long companyId,Long actor) {
        User u=userRepository.findById(actor).orElseThrow();flow.lockMember(companyId,u.getEmail(),true);return u;
    }
    private LetterRequest scoped(Long companyId,Long id) {
        var ids=db.queryForList("SELECT id FROM letter_requests WHERE company_id=? AND id=?",Long.class,companyId,id);
        if(ids.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Letter not found");
        return letterRequestRepository.findById(id).orElseThrow();
    }
    private LetterRequest requireSubmittedRequest(Long companyId,Long id,long version) {
        db.queryForList("SELECT id FROM letter_requests WHERE company_id=? AND id=? FOR UPDATE",Long.class,companyId,id);
        var request=scoped(companyId,id);
        if(request.getVersion()!=version)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Letter changed; reload before review");
        if(request.getStatus()!=VacationStatus.SUBMITTED)throw new IllegalArgumentException("Only submitted letters can be reviewed");return request;
    }
    private void notifyAdmins(LetterRequest request) {
        var admins=db.queryForList("SELECT DISTINCT r.user_id FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id JOIN users u ON u.id=r.user_id WHERE r.company_id=? AND r.role_key='COMPANY_ADMIN' AND r.project_id IS NULL AND r.removed_at IS NULL AND m.status='ACTIVE' AND u.is_active AND NOT u.admin_locked AND r.user_id<>? AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=r.user_id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL)",Long.class,request.getCompanyId(),request.getUser().getId());
        for(long admin:admins)notificationService.createNotification(admin,"LETTER_REQUEST_SUBMITTED","Letter request submitted","A company member requested a letter.",request.getId(),"LetterRequest");
    }
    private LetterRequestDTO toDTO(LetterRequest request) {
        return LetterRequestDTO.builder().companyId(request.getCompanyId()).version(request.getVersion())
                .id(request.getId())
                .userId(request.getUser().getId())
                .userName(request.getUser().getFullName())
                .employeeId(access.employmentEmployeeId(request.getCompanyId(),request.getUser().getId()))
                .email(request.getUser().getEmail())
                .jobTitle(access.employmentJobTitle(request.getCompanyId(),request.getUser().getId()))
                .userJoiningDate(db.queryForObject("SELECT joining_date FROM company_memberships WHERE company_id=? AND user_id=?",LocalDate.class,request.getCompanyId(),request.getUser().getId()))
                .requestType(request.getRequestType())
                .status(request.getStatus())
                .recipientOrganization(request.getRecipientOrganization())
                .recipientAddress(request.getRecipientAddress())
                .purpose(request.getPurpose())
                .requestedFullName(request.getRequestedFullName())
                .requestedJobTitle(request.getRequestedJobTitle())
                .employmentStartDate(request.getEmploymentStartDate())
                .approvedFullName(request.getApprovedFullName())
                .approvedJobTitle(request.getApprovedJobTitle())
                .approvedEmploymentStartDate(request.getApprovedEmploymentStartDate())
                .reviewNote(request.getReviewNote())
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
        if(request.getIssuedText()!=null)return request.getIssuedText();
        if(request.getStatus()!=VacationStatus.APPROVED){
            try {var d=templates.resolve(request.getCompanyId(),request.getRequestType(),null);return templates.render(d,request,null,LocalDate.now());}
            catch(IllegalArgumentException e){return "Unsigned draft — company letter setup is required before approval.";}
        }
        User user = request.getUser();
        String date = formatDate(request.getApprovedAt() == null ? LocalDate.now() : request.getApprovedAt().toLocalDate());
        String fullName = valueOrDefault(request.getApprovedFullName(), valueOrDefault(request.getRequestedFullName(), user.getFullName()));
        String jobTitle = valueOrDefault(request.getApprovedJobTitle(), valueOrDefault(request.getRequestedJobTitle(), "employee"));
        String employmentStart = formatDate(request.getApprovedEmploymentStartDate() == null
                ? request.getEmploymentStartDate() : request.getApprovedEmploymentStartDate());
        String company = access.companyDisplayName(request.getCompanyId());

        String body = switch (request.getRequestType()) {
            case EMPLOYMENT_VERIFICATION -> String.join("\n\n",
                    String.format("At the request of %s, this letter confirms their current employment with %s.", fullName, company),
                    String.format("%s has been employed since %s and currently holds the position of %s. Our records show that their employment is active as of the date of this letter.", fullName, employmentStart, jobTitle),
                    "This verification reflects our employment records and is provided for the recipient's use. For further confirmation, please contact our Human Resources department using the details on this letter.");
            case TRAVEL -> String.join("\n\n",
                    String.format("At the request of %s, this letter confirms their current employment with %s as %s. Their employment began on %s.", fullName, company, jobTitle, employmentStart),
                    String.format("According to the information provided with their request, %s plans to travel%s from %s through %s.", fullName, travelDestinationText(request), formatDate(request.getTravelStartDate()), formatDate(request.getTravelEndDate())),
                    String.format("%s remains an active employee and is expected to resume their work responsibilities after the stated travel period. This letter confirms employment and the travel dates supplied to us; it does not replace any required travel authorization.", fullName));
            case VACATION -> String.join("\n\n",
                    String.format("At the request of %s, this letter confirms their current employment with %s as %s. Their employment began on %s.", fullName, company, jobTitle, employmentStart),
                    String.format("%s has provided vacation dates of %s through %s. These dates are recorded with the company for administrative review and remain subject to the applicable leave approval process.", fullName, formatDate(request.getVacationStartDate()), formatDate(request.getVacationEndDate())),
                    String.format("%s is expected to resume their regular work responsibilities following any approved leave. Please contact our Human Resources department if further employment verification is needed.", fullName));
        };

        List<String> sections = new ArrayList<>();
        sections.add(company);
        sections.add(date);
        sections.add("");
        sections.add("To whom it may concern,");
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
        sections.add(request.getApprovedBy()==null?"Company administration":request.getApprovedBy().getFullName());
        sections.add("Company Admin");
        sections.add("");
        sections.add("Employer Information\n"+company);
        sections.add("");
        sections.add(company);
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
