package com.maxwell.chronos.service;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.enums.LetterRequestType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SharedLetterSettingsTest {
    final CompanyLetterTemplates service=new CompanyLetterTemplates(mock(org.springframework.jdbc.core.JdbcTemplate.class),new ObjectMapper(),mock(CompanyWorkflowAccess.class));
    @Test void sharedIdentityRestoresWordingForEveryLetterAndRendersCleanly() throws Exception {
        var employer=new CompanyLetterTemplates.Employer("company","Example Company","100 Business Street, Chicago, IL","hr@example.com","555-0100","example.com","","");
        var hr=new CompanyLetterTemplates.Signatory("hr","Casey HR","HR Director","");
        var config=service.shared(new CompanyLetterTemplates.Configuration(3,List.of(employer),List.of(hr),List.of()));
        assertEquals(3,config.templates().size());
        for(var t:config.templates()) {
            assertTrue(t.enabled());assertEquals("company",t.employerId());assertEquals("hr",t.signatoryId());
            var r=LetterRequest.builder().requestType(LetterRequestType.valueOf(t.type())).requestedFullName("Sam Lee").requestedJobTitle("Engineer").employmentStartDate(LocalDate.of(2025,1,1)).destinationCountry("Canada").travelStartDate(LocalDate.of(2026,11,1)).travelEndDate(LocalDate.of(2026,11,8)).vacationStartDate(LocalDate.of(2026,11,1)).vacationEndDate(LocalDate.of(2026,11,8)).build();
            var definition=service.definition(config,t,null);
            String text=service.render(definition,r,null,LocalDate.of(2026,10,6));
            assertTrue(text.contains("At the request of Sam Lee"));assertTrue(text.contains("January 1, 2025"));assertFalse(text.contains("{{"));
            var bytes=CompanyLetterPdf.render(definition.name(),t.type().replace('_',' '),"LTR-SAMPLE",true,text,definition);
            Path dir=Path.of("../reports/letter-cleanup-qa");Files.createDirectories(dir);Files.write(dir.resolve(t.type()+".pdf"),bytes);
            try(var pdf=PDDocument.load(bytes)){assertEquals(1,pdf.getNumberOfPages());ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,110),"png",dir.resolve(t.type()+".png").toFile());}
        }
    }
    @Test void incompleteSetupKeepsAllLettersUnavailable() {
        assertTrue(service.shared(service.defaults()).templates().stream().noneMatch(CompanyLetterTemplates.Template::enabled));
    }
}
