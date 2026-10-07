package com.maxwell.chronos.web;
import com.maxwell.chronos.service.CompanyWorkflowAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequiredArgsConstructor @RequestMapping("/companies/{companyId}")
public class CompanyGovernanceController {
    private final CompanyWorkflowAccess flow;
    private String email(Jwt jwt){return jwt.getClaimAsString("preferred_username");}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/sensitive-grants") public ResponseEntity<?> list(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(flow.grants(companyId,email(jwt)));}
    @PostMapping("/sensitive-grants") public ResponseEntity<?> grant(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyWorkflowAccess.Grant input){return response(flow.grant(companyId,email(jwt),input));}
    @DeleteMapping("/sensitive-grants/{id}") public ResponseEntity<?> revoke(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam long version){flow.revoke(companyId,email(jwt),id,version);return response(null);}
    @GetMapping("/confidential-configuration") public ResponseEntity<?> config(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return response(flow.configuration(companyId,email(jwt)));}
    @GetMapping("/audit") public ResponseEntity<?> audit(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page){return response(flow.audit(companyId,email(jwt),page));}
}
