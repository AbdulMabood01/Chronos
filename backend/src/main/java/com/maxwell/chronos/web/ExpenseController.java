package com.maxwell.chronos.web;

import com.maxwell.chronos.service.ExpenseService;
import com.maxwell.chronos.service.ExpenseService.Decision;
import com.maxwell.chronos.service.ExpenseService.Input;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenses;
    private String email(Jwt jwt) { return jwt.getClaimAsString("preferred_username"); }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    @GetMapping("/mine") public ResponseEntity<List<Map<String,Object>>> mine(@AuthenticationPrincipal Jwt jwt) { return response(expenses.mine(email(jwt))); }
    @GetMapping("/pending") public ResponseEntity<List<Map<String,Object>>> pending(@AuthenticationPrincipal Jwt jwt) { return response(expenses.pending(email(jwt))); }
    @GetMapping("/projects/{projectId}") public ResponseEntity<List<Map<String,Object>>> project(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId) { return response(expenses.projectExpenses(email(jwt),projectId)); }
    @GetMapping("/projects/{projectId}/totals") public ResponseEntity<Map<String,Object>> totals(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId) { return response(expenses.totals(email(jwt),projectId)); }
    @GetMapping("/projects/{projectId}/budget-check") public ResponseEntity<Map<String,Object>> budgetCheck(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,@RequestParam java.math.BigDecimal amount,@RequestParam(required=false) Long excludeExpenseId) { return response(expenses.budgetCheck(email(jwt),projectId,amount,excludeExpenseId)); }
    @GetMapping("/{id}") public ResponseEntity<Map<String,Object>> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return response(expenses.detail(email(jwt),id)); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<Map<String,Object>> submit(@AuthenticationPrincipal Jwt jwt,@RequestPart("expense") Input input,@RequestPart(value="receipt",required=false) MultipartFile receipt) { return response(expenses.submit(email(jwt),input,receipt)); }
    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<Map<String,Object>> resubmit(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestPart("expense") Input input,@RequestPart(value="receipt",required=false) MultipartFile receipt) { return response(expenses.resubmit(email(jwt),id,input,receipt)); }
    @PostMapping("/{id}/decision") public ResponseEntity<Map<String,Object>> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody Decision decision) { return response(expenses.decide(email(jwt),id,decision)); }
    @GetMapping("/{id}/receipt") public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        var file = expenses.receipt(email(jwt),id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString()).body(file.bytes());
    }
}
