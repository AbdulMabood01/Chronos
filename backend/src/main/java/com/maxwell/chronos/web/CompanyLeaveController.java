package com.maxwell.chronos.web;
import com.maxwell.chronos.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/companies/{companyId}/leave") @RequiredArgsConstructor
public class CompanyLeaveController {
    private final CompanyLeaveService leave;
    private final UserService users;
    private long actor(Jwt jwt){return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();}
    @GetMapping("/my") public List<Map<String,Object>> mine(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return leave.mine(companyId,actor(jwt));}
    @GetMapping("/requests") public List<Map<String,Object>> requests(@PathVariable long companyId,@AuthenticationPrincipal Jwt jwt){return leave.queue(companyId,actor(jwt));}
    @PostMapping("/requests") public Map<String,Object> create(@PathVariable long companyId,@Valid @RequestBody CompanyLeaveService.RequestInput input,@AuthenticationPrincipal Jwt jwt){return leave.save(companyId,null,actor(jwt),input);}
    @PutMapping("/requests/{id}") public Map<String,Object> edit(@PathVariable long companyId,@PathVariable long id,@Valid @RequestBody CompanyLeaveService.RequestInput input,@AuthenticationPrincipal Jwt jwt){return leave.save(companyId,id,actor(jwt),input);}
    @PostMapping("/requests/{id}/submit") public Map<String,Object> submit(@PathVariable long companyId,@PathVariable long id,@RequestParam long version,@AuthenticationPrincipal Jwt jwt){return leave.submit(companyId,id,actor(jwt),version);}
    @DeleteMapping("/requests/{id}") public void delete(@PathVariable long companyId,@PathVariable long id,@RequestParam long version,@AuthenticationPrincipal Jwt jwt){leave.delete(companyId,id,actor(jwt),version);}
    @PostMapping("/requests/{id}/approve") public Map<String,Object> approve(@PathVariable long companyId,@PathVariable long id,@Valid @RequestBody CompanyLeaveService.Decision input,@AuthenticationPrincipal Jwt jwt){return leave.decide(companyId,id,actor(jwt),true,input);}
    @PostMapping("/requests/{id}/reject") public Map<String,Object> reject(@PathVariable long companyId,@PathVariable long id,@Valid @RequestBody CompanyLeaveService.Decision input,@AuthenticationPrincipal Jwt jwt){return leave.decide(companyId,id,actor(jwt),false,input);}
    @GetMapping("/balance/me") public CompanyLeaveService.Balance mineBalance(@PathVariable long companyId,@RequestParam int year,@AuthenticationPrincipal Jwt jwt){long actor=actor(jwt);return leave.balance(companyId,actor,year,actor);}
    @GetMapping("/balance/{userId}") public CompanyLeaveService.Balance balance(@PathVariable long companyId,@PathVariable long userId,@RequestParam int year,@AuthenticationPrincipal Jwt jwt){return leave.balance(companyId,userId,year,actor(jwt));}
    @PutMapping("/balance/{userId}") public CompanyLeaveService.Balance allowance(@PathVariable long companyId,@PathVariable long userId,@Valid @RequestBody CompanyLeaveService.AllowanceInput input,@AuthenticationPrincipal Jwt jwt){return leave.allowance(companyId,userId,actor(jwt),input);}
    @GetMapping("/policy/preview") public Map<String,Object> preview(@PathVariable long companyId,@RequestParam int year,@AuthenticationPrincipal Jwt jwt){return leave.preview(companyId,actor(jwt),year);}
    @PostMapping("/policy/apply") public Map<String,Object> apply(@PathVariable long companyId,@Valid @RequestBody CompanyLeaveService.PolicyInput input,@AuthenticationPrincipal Jwt jwt){return leave.publish(companyId,actor(jwt),input);}
    @GetMapping("/calendar") public List<Map<String,Object>> calendar(@PathVariable long companyId,@RequestParam int year,@RequestParam int month,@AuthenticationPrincipal Jwt jwt){return leave.calendar(companyId,actor(jwt),year,month);}
}
