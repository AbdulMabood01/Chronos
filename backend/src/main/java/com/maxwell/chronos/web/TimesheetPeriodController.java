package com.maxwell.chronos.web;

import com.maxwell.chronos.service.TimesheetPeriodService;
import com.maxwell.chronos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timesheet-periods")
@RequiredArgsConstructor
public class TimesheetPeriodController {
    private final TimesheetPeriodService periods;
    private final UserService users;
    private long actor(Jwt jwt) { return users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId(); }
    public record Decision(boolean approve,String comment,String fallbackReason) {}
    public record Opening(String reason) {}
    public record BatchSubmission(List<LocalDate> dates) {}
    @GetMapping("/month") public List<Map<String,Object>> month(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId,@RequestParam LocalDate date,@RequestParam(required=false) Long userId) {
        long actor=actor(jwt); return periods.month(actor,userId==null?actor:userId,projectId,date);
    }
    @PostMapping("/submit-batch") public List<Map<String,Object>> submitBatch(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId,@RequestBody BatchSubmission submission) {
        return periods.submitBatch(actor(jwt),projectId,submission.dates());
    }
    @GetMapping public Map<String,Object> get(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId,@RequestParam LocalDate date,@RequestParam(required=false) Long userId) {
        long actor=actor(jwt); return periods.view(actor,userId==null?actor:userId,projectId,date);
    }
    @PostMapping("/submit") public Map<String,Object> submit(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId,@RequestParam LocalDate date) {
        return periods.submit(actor(jwt),projectId,date);
    }
    @PostMapping("/{id}/decision") public Map<String,Object> decide(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody Decision decision) {
        return periods.decide(actor(jwt),id,decision.approve(),decision.comment(),decision.fallbackReason());
    }
    @PostMapping("/opening") public Map<String,Object> opening(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId,@RequestParam LocalDate date,@RequestBody Opening opening) {
        return periods.requestOpening(actor(jwt),projectId,date,opening.reason());
    }
    @PostMapping("/{id}/opening-decision") public Map<String,Object> decideOpening(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody Decision decision) {
        return periods.decideOpening(actor(jwt),id,decision.approve(),decision.comment());
    }
    @GetMapping("/pending") public List<Map<String,Object>> pending(@AuthenticationPrincipal Jwt jwt) { return periods.pending(actor(jwt)); }
    @GetMapping("/{id}/history") public List<Map<String,Object>> history(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return periods.history(actor(jwt),id); }
    @GetMapping("/openings/pending") public List<Map<String,Object>> pendingOpenings(@AuthenticationPrincipal Jwt jwt) { return periods.pendingOpenings(actor(jwt)); }
}
