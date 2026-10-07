package com.maxwell.chronos.web;
import com.maxwell.chronos.dto.*;
import com.maxwell.chronos.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor @RequestMapping("/companies/{companyId}/letter-requests")
public class CompanyLetterRequestController {
    private final CompanyLetterRequestService service;
    private final CompanyWorkflowAccess flow;
    public record Decision(@NotNull @Min(0) Long version,@Size(max=200) String fullName,@Size(max=120) String jobTitle,LocalDate employmentStartDate,@Size(max=500) String reviewNote,@Size(max=500) String reason,Long configurationVersion,CompanyLetterTemplates.Definition letter) {
        public Decision(Long version,String name,String title,LocalDate date,String note,String reason){this(version,name,title,date,note,reason,null,null);}
        public LetterReviewRequest review(){return new LetterReviewRequest(fullName,jobTitle,employmentStartDate,reviewNote,configurationVersion,letter);}
    }
    private long actor(long company,Jwt jwt){return flow.member(company,jwt.getClaimAsString("preferred_username")).getId();}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/my") public ResponseEntity<?> mine(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(service.getMyRequests(companyId,actor(companyId,jwt)));}
    @GetMapping("/pending") public ResponseEntity<?> pending(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(service.getPendingRequests(companyId,actor(companyId,jwt)));}
    @PostMapping public ResponseEntity<?> create(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@RequestBody CreateLetterRequest input){return response(service.createLetterRequest(companyId,actor(companyId,jwt),input));}
    @GetMapping("/{id}") public ResponseEntity<?> detail(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return response(service.getRequest(companyId,id,actor(companyId,jwt),false));}
    @PostMapping("/{id}/approve") public ResponseEntity<?> approve(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Decision input){return response(service.approveLetterRequest(companyId,id,actor(companyId,jwt),input.review(),input.version()));}
    @PostMapping("/{id}/preview") public ResponseEntity<?> preview(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Decision input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).body(service.previewReview(companyId,id,actor(companyId,jwt),input.review(),input.version()));}
    @PostMapping("/{id}/reject") public ResponseEntity<?> reject(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Decision input){return response(service.rejectLetterRequest(companyId,id,input.reason(),actor(companyId,jwt),input.version()));}
    @GetMapping("/{id}/pdf") public ResponseEntity<?> pdf(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable long id){long actor=actor(companyId,jwt);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(service.buildFilename(companyId,id,actor,false)).build().toString()).body(service.generatePdf(companyId,id,actor,false));}
}
