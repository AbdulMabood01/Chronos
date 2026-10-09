package com.maxwell.chronos.web;
import com.maxwell.chronos.service.CompanyLetterTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.*;

@RestController @RequiredArgsConstructor @RequestMapping("/companies/{companyId}/letter-settings")
public class CompanyLetterSettingsController {
    private final CompanyLetterTemplates templates;
    private ResponseEntity<?> response(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<?> get(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(templates.adminRead(companyId,jwt.getClaimAsString("preferred_username")));}
    @PutMapping public ResponseEntity<?> save(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@RequestBody CompanyLetterTemplates.Configuration input){return response(templates.save(companyId,jwt.getClaimAsString("preferred_username"),input));}
    @GetMapping("/availability") public ResponseEntity<?> available(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(templates.availability(companyId,jwt.getClaimAsString("preferred_username")));}
    public record Preview(CompanyLetterTemplates.Configuration configuration,String type) {}
    @PostMapping("/preview") public ResponseEntity<?> preview(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@RequestBody Preview input){
        templates.adminRead(companyId,jwt.getClaimAsString("preferred_username"));templates.validate(input.configuration());
        var t=input.configuration().templates().stream().filter(x->x.type().equals(input.type())).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a letter type."));
        var d=templates.definition(input.configuration(),t,null);templates.validateDefinition(d,true);d=templates.unsigned(d);
        var r=com.maxwell.chronos.domain.LetterRequest.builder().requestType(com.maxwell.chronos.enums.LetterRequestType.valueOf(t.type())).requestedFullName("Sample Employee").requestedJobTitle("Sample position").employmentStartDate(java.time.LocalDate.of(2025,1,1)).travelStartDate(java.time.LocalDate.now()).travelEndDate(java.time.LocalDate.now().plusDays(7)).vacationStartDate(java.time.LocalDate.now()).vacationEndDate(java.time.LocalDate.now().plusDays(7)).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).body(templates.previewTemplate(d,r));
    }
}
