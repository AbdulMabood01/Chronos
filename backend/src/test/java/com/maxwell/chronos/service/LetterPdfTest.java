package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.Optional;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LetterPdfTest {
    private final LetterRequestRepository repository = mock(LetterRequestRepository.class);
    private final LetterRequestService service = new LetterRequestService(repository, mock(UserRepository.class), mock(AuditService.class), mock(NotificationService.class));
    private LetterRequest letter(LetterRequestType type) {
        var request = LetterRequest.builder().id(42L).user(User.builder().id(1L).firstName("Jordan").lastName("Rivera").jobTitle("Software Engineer").build())
                .requestType(type).status(VacationStatus.APPROVED).requestedFullName("Jordan Rivera").requestedJobTitle("Software Engineer")
                .employmentStartDate(LocalDate.of(2024, 6, 3)).destinationCountry("Canada")
                .travelStartDate(LocalDate.of(2026, 10, 5)).travelEndDate(LocalDate.of(2026, 10, 12))
                .vacationStartDate(LocalDate.of(2026, 10, 5)).vacationEndDate(LocalDate.of(2026, 10, 12)).build();
        when(repository.findById(42L)).thenReturn(Optional.of(request));
        return request;
    }
    @ParameterizedTest @EnumSource(LetterRequestType.class)
    void allLetterTypesContainLogoContentAndCompanyDetails(LetterRequestType type) throws Exception {
        letter(type);
        byte[] bytes = service.generatePdf(42L, 1L, false);
        try (var pdf = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Jordan Rivera"));
            assertTrue(text.contains("LTR-42"));
            assertTrue(text.contains("June 3, 2024"));
            assertTrue(text.contains("E-Verification Number: 2601516"));
            assertTrue(text.contains("hr@maxwellnetwork.org"));
            assertTrue(text.contains("Syed Hussain"));
            assertTrue(text.contains("Best regards,"));
            assertFalse(text.contains("To whomsoever"));
            assertFalse(text.contains("PREVIEW - NOT APPROVED"));
            var images = 0;
            for (var page : pdf.getPages())
                for (var name : page.getResources().getXObjectNames())
                    if (page.getResources().isImageXObject(name)) images++;
            assertTrue(images >= 2, "Letter should include both logo and manager signature");
            Path output = Path.of("target", "letter-preview"); Files.createDirectories(output);
            Files.write(output.resolve(type + ".pdf"), bytes);
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 105), "png", output.resolve(type + ".png").toFile());
        }
    }
    @Test void longLetterKeepsLastParagraphAndSignature() throws Exception {
        var request = letter(LetterRequestType.TRAVEL);
        request.setNotes("Complete project handover before travel. ".repeat(120) + "END-OF-NOTES");
        try (var pdf = PDDocument.load(service.generatePdf(42L, 1L, false))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(pdf.getNumberOfPages() > 1);
            assertTrue(text.contains("END-OF-NOTES"));
            assertTrue(text.indexOf("END-OF-NOTES") < text.indexOf("Best regards,"));
            assertTrue(text.contains("Page " + pdf.getNumberOfPages() + " of " + pdf.getNumberOfPages()));
        }
    }
    @Test void unapprovedAdminPreviewIsClearlyMarkedAndEmployeeCannotDownloadIt() throws Exception {
        var request = letter(LetterRequestType.VACATION); request.setStatus(VacationStatus.SUBMITTED);
        assertThrows(IllegalArgumentException.class, () -> service.generatePdf(42L, 1L, false));
        try (var pdf = PDDocument.load(service.generatePdf(42L, 99L, true))) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("PREVIEW - NOT APPROVED"));
        }
    }
    @Test void employeeCannotDownloadAnotherEmployeesLetter() {
        letter(LetterRequestType.EMPLOYMENT_VERIFICATION);
        assertThrows(IllegalArgumentException.class, () -> service.generatePdf(42L, 2L, false));
    }
}
