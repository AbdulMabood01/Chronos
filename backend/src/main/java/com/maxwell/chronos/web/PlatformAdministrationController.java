package com.maxwell.chronos.web;
import com.maxwell.chronos.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/platform")
public class PlatformAdministrationController {
    private final PlatformAdministrationService service;
    private final CompanyManagementService companies;
    private final UserService users;
    private String email(Jwt jwt){return jwt.getClaimAsString("preferred_username");}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/companies") public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt){return response(service.companies(email(jwt)));}
    @GetMapping("/companies/{id}/usage") public ResponseEntity<?> usage(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return response(service.usage(id,email(jwt)));}
    @PutMapping("/companies/{id}/plan") public ResponseEntity<?> plan(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody PlatformAdministrationService.Plan input){service.plan(id,email(jwt),input);return response(null);}
    @PutMapping("/companies/{id}/status") public ResponseEntity<?> status(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody PlatformAdministrationService.Status input){service.status(id,email(jwt),input);return response(null);}
    @GetMapping("/companies/{id}/admin-invitations") public ResponseEntity<?> invitations(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return response(service.adminInvitations(id,email(jwt)));}
    @PostMapping("/companies/{id}/admin-invitations/{invitation}/resend") public ResponseEntity<?> resend(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@PathVariable long invitation){service.company(id,email(jwt));companies.resendInvitation(id,invitation,users.findUserEntityByEmail(email(jwt)).getId());return response(null);}
    @DeleteMapping("/companies/{id}/admin-invitations/{invitation}") public ResponseEntity<?> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@PathVariable long invitation){service.company(id,email(jwt));companies.revokePlatformAdminInvitation(id,invitation,users.findUserEntityByEmail(email(jwt)).getId());return response(null);}
    @GetMapping("/accounts") public ResponseEntity<?> accounts(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String query){return response(service.accounts(email(jwt),query));}
    @PostMapping("/accounts/{id}/actions") public ResponseEntity<?> account(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody PlatformAdministrationService.AccountAction input){service.account(id,email(jwt),input);return response(null);}
    @PostMapping("/administrators") public ResponseEntity<?> administrator(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PlatformAdministrationService.AdminInput input){return response(Map.of("id",service.createAdmin(email(jwt),input)));}
    @GetMapping("/audit") public ResponseEntity<?> audit(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page){return response(service.audit(email(jwt),page));}
}
