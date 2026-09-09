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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

        return buildSimplePdf(buildLetterText(request));
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
        if (!admin.isSuperAdmin() && !admin.isAdmin()) {
            throw new IllegalArgumentException("Only Admins can approve letter requests");
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
        admins.addAll(userRepository.findByRole(UserRole.ADMIN));
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

    private byte[] buildSimplePdf(String text) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            drawLetterhead(document, page);
            writeLetterContent(document, page, text);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drawLetterhead(PDDocument document, PDPage page) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.setNonStrokingColor(255, 255, 255);
            content.addRect(0, 0, mediaBox.getWidth(), mediaBox.getHeight());
            content.fill();

            drawHeaderBrand(document, content, mediaBox);

            content.setStrokingColor(212, 222, 233);
            content.setLineWidth(0.8f);
            content.moveTo(72, mediaBox.getHeight() - 122);
            content.lineTo(mediaBox.getWidth() - 72, mediaBox.getHeight() - 122);
            content.stroke();

            content.setStrokingColor(212, 222, 233);
            content.setLineWidth(0.8f);
            content.moveTo(72, 122);
            content.lineTo(mediaBox.getWidth() - 72, 122);
            content.stroke();
        }
    }

    private void drawHeaderBrand(PDDocument document, PDPageContentStream content, PDRectangle mediaBox) throws IOException {
        String companyName = "Maxwell Network Inc";
        float centerX = mediaBox.getWidth() / 2;
        float companyY = mediaBox.getHeight() - 82;
        float logoY = mediaBox.getHeight() - 84;
        float logoHeight = 58;
        float companyFontSize = 20;
        try (InputStream logoStream = getClass().getResourceAsStream("/Logo.png")) {
            if (logoStream != null) {
                PDImageXObject logo = PDImageXObject.createFromByteArray(document, logoStream.readAllBytes(), "Logo.png");
                float logoWidth = logoHeight * logo.getWidth() / logo.getHeight();
                float textWidth = textWidth(companyName, PDType1Font.HELVETICA_BOLD, companyFontSize);
                float logoX = centerX - (textWidth / 2) - logoWidth - 43;
                content.drawImage(logo, logoX, logoY, logoWidth, logoHeight);
            }
        }

        content.setNonStrokingColor(0, 51, 88);
        drawCenteredText(content, centerX, companyY, companyName, PDType1Font.HELVETICA_BOLD, companyFontSize);
        content.setNonStrokingColor(34, 41, 47);
    }

    private void writeLetterContent(PDDocument document, PDPage page, String text) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        float marginX = 72;
        float y = mediaBox.getHeight() - 146;
        float maxWidth = mediaBox.getWidth() - 144;
        float bodyBottom = 308;

        try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            content.setNonStrokingColor(34, 41, 47);
            String[] sections = text.split("\\R\\R");
            String employerSection = "";
            String footerSection = "";
            int employerSectionIndex = -1;
            int footerSectionIndex = -1;

            for (int index = 0; index < sections.length; index++) {
                String section = sections[index].trim();
                if (section.startsWith("Employer Information")) {
                    employerSection = section;
                    employerSectionIndex = index;
                } else if (section.startsWith("tech.maxwellnetwork.org")) {
                    footerSection = section;
                    footerSectionIndex = index;
                }
            }

            int bodySectionCount = employerSectionIndex >= 0 ? employerSectionIndex : sections.length;
            if (footerSectionIndex >= 0 && footerSectionIndex < bodySectionCount) {
                bodySectionCount = footerSectionIndex;
            }

            for (int index = 0; index < sections.length; index++) {
                if (index >= bodySectionCount) {
                    break;
                }
                String section = sections[index].trim();
                if (section.isBlank()) {
                    y -= 8;
                    continue;
                }

                if (index == 0) {
                    String[] headerLines = section.split("\\R");
                    if (headerLines.length > 1) {
                        drawRightAlignedText(content, mediaBox.getWidth() - marginX, y, headerLines[1], PDType1Font.HELVETICA, 10.5f);
                        y -= 34;
                    }
                    continue;
                }

                boolean subject = section.startsWith("Subject:");
                boolean salutation = section.toLowerCase().startsWith("to whomsoever");
                boolean signature = section.startsWith("Best Regards,");
                if (signature) {
                    drawSignatureBlock(content, marginX, Math.max(y, 326));
                    y = 306;
                    continue;
                }
                PDType1Font font = subject || salutation || signature ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
                float fontSize = subject || salutation ? 11.5f : 10.7f;
                float leading = subject || signature ? 15 : 14.5f;
                String[] paragraphLines = section.split("\\R");
                for (int paragraphIndex = 0; paragraphIndex < paragraphLines.length; paragraphIndex++) {
                    String paragraphLine = paragraphLines[paragraphIndex];
                    for (String line : wrapLine(paragraphLine, font, fontSize, maxWidth)) {
                        if (y < bodyBottom) {
                            break;
                        }
                        if (salutation) {
                            drawCenteredText(content, mediaBox.getWidth() / 2, y, line, font, fontSize);
                        } else {
                            drawText(content, marginX, y, line, font, fontSize);
                        }
                        y -= leading;
                    }
                }
                y -= subject ? 13 : 9;
            }

            drawEmployerInformation(content, marginX, 240, maxWidth, employerSection);
            drawCompanyFooter(content, mediaBox.getWidth() / 2, footerSection);
        }
    }

    private void drawText(PDPageContentStream content, float x, float y, String text, PDType1Font font, float fontSize) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(sanitizePdfText(text));
        content.endText();
    }

    private void drawRightAlignedText(PDPageContentStream content, float rightX, float y, String text, PDType1Font font, float fontSize) throws IOException {
        float x = rightX - textWidth(text, font, fontSize);
        drawText(content, x, y, text, font, fontSize);
    }

    private void drawCenteredText(PDPageContentStream content, float centerX, float y, String text, PDType1Font font, float fontSize) throws IOException {
        float x = centerX - (textWidth(text, font, fontSize) / 2);
        drawText(content, x, y, text, font, fontSize);
    }

    private void drawSignatureLine(PDPageContentStream content, float x, float y) throws IOException {
        content.setStrokingColor(116, 132, 148);
        content.setLineWidth(0.6f);
        content.moveTo(x, y);
        content.lineTo(x + 190, y);
        content.stroke();
        content.setNonStrokingColor(34, 41, 47);
    }

    private void drawSignatureBlock(PDPageContentStream content, float x, float y) throws IOException {
        drawText(content, x, y, "Best Regards,", PDType1Font.HELVETICA_BOLD, 10.7f);
        drawSignatureLine(content, x, y - 42);
        drawText(content, x, y - 58, "Syed Hussain", PDType1Font.HELVETICA, 10.7f);
        drawText(content, x, y - 73, "Manager", PDType1Font.HELVETICA, 10.2f);
    }

    private void drawFooterRule(PDPageContentStream content, float x, float y, float width) throws IOException {
        content.setStrokingColor(190, 202, 215);
        content.setLineWidth(0.8f);
        content.moveTo(x, y);
        content.lineTo(x + width, y);
        content.stroke();
        content.setNonStrokingColor(34, 41, 47);
    }

    private void drawEmployerInformation(PDPageContentStream content, float marginX, float topY, float maxWidth, String employerSection) throws IOException {
        if (employerSection == null || employerSection.isBlank()) {
            return;
        }

        float y = topY;
        content.setNonStrokingColor(236, 244, 255);
        content.addRect(marginX - 12, y - 126, maxWidth + 24, 148);
        content.fill();

        content.setStrokingColor(0, 102, 204);
        content.setLineWidth(1.1f);
        content.moveTo(marginX, y);
        content.lineTo(marginX + maxWidth, y);
        content.stroke();

        String[] lines = employerSection.split("\\R");
        drawText(content, marginX + 16, y - 12, lines[0], PDType1Font.HELVETICA_BOLD, 11.2f);
        y -= 36;

        content.setNonStrokingColor(72, 84, 96);
        float labelRightX = marginX + 212;
        float valueX = marginX + 238;
        for (int index = 1; index < lines.length; index++) {
            String[] parts = lines[index].split(":", 2);
            String label = parts[0] + ":";
            String value = parts.length > 1 ? parts[1].trim() : "";
            drawRightAlignedText(content, labelRightX, y, label, PDType1Font.HELVETICA_BOLD, 9.8f);
            drawText(content, valueX, y, value, PDType1Font.HELVETICA, 9.8f);
            y -= 18.5f;
        }
        content.setNonStrokingColor(34, 41, 47);
    }

    private void drawCompanyFooter(PDPageContentStream content, float centerX, String footerSection) throws IOException {
        if (footerSection == null || footerSection.isBlank()) {
            return;
        }

        String[] lines = footerSection.split("\\R");
        float y = 50;
        content.setNonStrokingColor(0, 102, 204);
        drawCenteredText(content, centerX, y, lines[0], PDType1Font.HELVETICA_BOLD, 8.8f);
        y -= 13;

        content.setNonStrokingColor(72, 84, 96);
        if (lines.length > 1) {
            drawCenteredText(content, centerX, y, lines[1], PDType1Font.HELVETICA, 8.2f);
        }
        content.setNonStrokingColor(34, 41, 47);
    }

    private List<String> wrapLine(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth(candidate, font, fontSize) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private float textWidth(String text, PDType1Font font, float fontSize) throws IOException {
        return font.getStringWidth(sanitizePdfText(text)) / 1000 * fontSize;
    }

    private String sanitizePdfText(String value) {
        return value.replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u00a0', ' ')
                .replaceAll("[^\\x20-\\x7E]", "");
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
