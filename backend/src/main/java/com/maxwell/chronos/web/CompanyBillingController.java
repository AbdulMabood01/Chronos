package com.maxwell.chronos.web;

import com.maxwell.chronos.service.CompanyBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class CompanyBillingController {
    private final CompanyBillingService billing;
    private String email(Jwt jwt){return jwt.getClaimAsString("preferred_username");}
    private ResponseEntity<?> response(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/billing/catalog") public ResponseEntity<?> catalog(){return response(billing.catalog());}
    @GetMapping("/companies/{company}/billing") public ResponseEntity<?> summary(@PathVariable long company,@AuthenticationPrincipal Jwt jwt){return response(billing.summary(company,email(jwt)));}
    @PutMapping("/companies/{company}/billing/profile") public ResponseEntity<?> profile(@PathVariable long company,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyBillingService.Profile input){billing.updateProfile(company,email(jwt),input);return response(null);}
    @PostMapping("/companies/{company}/billing/quotes") public ResponseEntity<?> quote(@PathVariable long company,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyBillingService.Selection input){return response(billing.quote(company,email(jwt),input));}
    @PostMapping("/companies/{company}/billing/purchases/{id}/checkout") public ResponseEntity<?> checkout(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return response(billing.checkout(company,email(jwt),id));}
    @GetMapping("/companies/{company}/billing/purchases/{id}") public ResponseEntity<?> purchase(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return response(billing.purchaseStatus(company,email(jwt),id));}
    @PostMapping("/companies/{company}/billing/purchases/{id}/cancel") public ResponseEntity<?> cancel(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){billing.cancelCheckout(company,email(jwt),id);return response(null);}
    @PostMapping("/companies/{company}/billing/trial") public ResponseEntity<?> trial(@PathVariable long company,@AuthenticationPrincipal Jwt jwt){billing.trial(company,email(jwt));return response(null);}
    @PutMapping("/companies/{company}/billing/renewal-preference") public ResponseEntity<?> reduce(@PathVariable long company,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyBillingService.Reduction input){billing.reduce(company,email(jwt),input);return response(null);}
    @PutMapping("/companies/{company}/billing/members/{user}/employee-access") public ResponseEntity<?> workforce(@PathVariable long company,@PathVariable long user,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyBillingService.Workforce input){billing.workforce(company,user,email(jwt),input);return response(null);}
    @GetMapping("/companies/{company}/billing/receipts/{id}") public ResponseEntity<byte[]> receipt(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"chronos-receipt-"+id+".pdf\"").contentType(MediaType.APPLICATION_PDF).body(billing.receipt(company,email(jwt),id));}
    @PostMapping("/companies/{company}/billing/receipts/{id}/reissue") public ResponseEntity<?> reissue(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){billing.reissue(company,email(jwt),id);return response(null);}
    @PostMapping("/companies/{company}/billing/purchases/{id}/refund") public ResponseEntity<?> refund(@PathVariable long company,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CompanyBillingService.Refund input){return response(billing.refund(company,email(jwt),id,input));}
    @PostMapping("/billing/stripe/webhook") public ResponseEntity<?> webhook(@RequestBody byte[] raw,@RequestHeader(value="Stripe-Signature",required=false) String signature){billing.webhook(raw,signature);return response(null);}
}
