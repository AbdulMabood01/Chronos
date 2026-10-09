package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.ProjectRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyFeedbackReviewService {
    public enum Category { APPRECIATION, GENERAL, COLLABORATION, COMMUNICATION, IMPROVEMENT, OTHER }
    public record Feedback(@NotNull Long employeeId, @NotBlank @Size(max=20000) String content,
                           Category category, boolean anonymous) {}
    public record Review(@NotNull Long employeeId, @Min(1900) @Max(9999) int year,
        @Min(1) @Max(4) int quarter, @NotBlank @Size(max=20000) String summary,
        @Size(max=20000) String accomplishments, @Size(max=20000) String strengths,
        @Size(max=20000) String improvements, @Size(max=20000) String goals,
        @Size(max=20000) String comments, @Min(0) int version) {}
    private final JdbcTemplate db;
    private final CompanyWorkflowAccess flow;
    private final EmailAlertService emailAlerts;

    private User actor(long company,String email, boolean admin) { return flow.lockMember(company,email,false); }
    private void employee(long company,Long id) { if(id==null)throw new IllegalArgumentException("Select a company member");flow.target(company,id); }
    public List<Map<String,Object>> search(long company,String email, String query) {
        actor(company,email, false);
        if (query == null || query.trim().length() < 2) return List.of();
        if (query.length() > 200) throw new IllegalArgumentException("Search is too long");
        String term = query.trim().toLowerCase(Locale.ROOT);
        var rows=db.queryForList("""
            SELECT u.id,u.first_name,u.last_name,u.email FROM users u JOIN company_memberships m ON m.user_id=u.id WHERE m.company_id=? AND m.status='ACTIVE' AND u.is_active=true AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL)
            AND (strpos(lower(first_name || ' ' || last_name),?)>0 OR strpos(lower(email),?)>0)
            ORDER BY u.first_name,u.last_name,u.id LIMIT 30
            """, company,term, term);
        var actor=flow.member(company,email);
        for(var row:rows)row.put("can_review",actor.getId().longValue()!=((Number)row.get("id")).longValue()&&flow.permitted(company,actor.getId(),"PERFORMANCE_REVIEW",((Number)row.get("id")).longValue()));
        return rows;
    }
    public UUID submit(long company,String email, Feedback input) {
        User sender = actor(company,email, false);
        employee(company,input.employeeId());
        if (sender.getId().equals(input.employeeId())) throw new IllegalArgumentException("Choose another employee");
        UUID id = UUID.randomUUID();
        String type = "Employee";
        db.update("INSERT INTO employee_feedback(company_id,id,sender_id,recipient_id,content,category,anonymous,sender_type) VALUES (?,?,?,?,?,?,?,?)",
            company,id, sender.getId(), input.employeeId(), input.content().trim(), input.category() == null ? null : input.category().name(), input.anonymous(), type);
        emailAlerts.enqueueCompany(company,input.employeeId(), EmailAlertService.Category.FEEDBACK, "Chronos: feedback received", "/feedback",id,false);
        return id;
    }
    public List<Map<String,Object>> feedback(long company,String email, boolean given) {
        User user = actor(company,email, false);
        // Recipient projection never selects sender identifiers, including for anonymous records.
        String projection = given
            ? "u.first_name || ' ' || u.last_name AS recipient_name,u.email AS recipient_email"
            : "CASE WHEN f.anonymous THEN NULL ELSE u.first_name || ' ' || u.last_name END AS sender_name";
        return db.queryForList("SELECT f.id,f.content,f.category,f.anonymous,f.sender_type,f.submitted_at," + projection
            + " FROM employee_feedback f JOIN users u ON u.id=f." + (given ? "recipient_id" : "sender_id")
            + " WHERE f.company_id=? AND f." + (given ? "sender_id" : "recipient_id") + "=? ORDER BY f.submitted_at DESC,f.id", company,user.getId());
    }
    public List<Map<String,Object>> reviews(long company,String email, Long employeeId) {
        User user = actor(company,email, false);
        if(employeeId!=null && !employeeId.equals(user.getId()))flow.reviewer(company,user.getId(),employeeId);
        var rows=db.queryForList("SELECT r.*,u.first_name,u.last_name,u.email AS employee_email,concat_ws(' ',u.first_name,u.last_name) AS employee_name FROM performance_reviews r JOIN users u ON u.id=r.employee_id WHERE r.company_id=? AND (?::bigint IS NULL OR r.employee_id=?) AND ((r.employee_id=? AND r.published_at IS NOT NULL) OR (r.employee_id<>? AND EXISTS(SELECT 1 FROM company_sensitive_grants g WHERE g.company_id=r.company_id AND g.user_id=? AND g.permission='PERFORMANCE_REVIEW' AND g.subject_user_id=r.employee_id AND g.revoked_at IS NULL AND g.starts_on<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND g.ends_on>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date))) ORDER BY r.review_year DESC,r.quarter DESC,r.modified_at DESC,r.id",company,employeeId,employeeId,user.getId(),user.getId(),user.getId());
        for(var row:rows)row.put("can_manage",user.getId().longValue()!=((Number)row.get("employee_id")).longValue());
        return rows;
    }

    public UUID save(long company,String email, UUID id, Review input) {
        User admin = actor(company,email, true);
        employee(company,input.employeeId());
        flow.reviewer(company,admin.getId(),input.employeeId());
        if (id == null) {
            id = UUID.randomUUID();
            db.update("""
                INSERT INTO performance_reviews(company_id,id,employee_id,review_year,quarter,summary,accomplishments,strengths,improvements,goals,comments,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, company,id,input.employeeId(),input.year(),input.quarter(),input.summary().trim(),input.accomplishments(),input.strengths(),input.improvements(),input.goals(),input.comments(),admin.getId());
            audit(id,admin.getId(),"CREATED");
        } else {
            var previous = locked(company,id);
            if (!Objects.equals(previous.get("employee_id"),input.employeeId()) || ((Number)previous.get("review_year")).intValue()!=input.year() || ((Number)previous.get("quarter")).intValue()!=input.quarter())
                throw new IllegalArgumentException("Employee and review period cannot be changed");
            if (((Number)previous.get("version")).intValue()!=input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Review changed; reload before saving");
            db.update("UPDATE performance_reviews SET summary=?,accomplishments=?,strengths=?,improvements=?,goals=?,comments=?,published_at=NULL,modified_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
                input.summary().trim(),input.accomplishments(),input.strengths(),input.improvements(),input.goals(),input.comments(),id);
            audit(id,admin.getId(),"EDITED");
        }
        return id;
    }
    public void publish(long company,String email, UUID id, int version) {
        User admin = actor(company,email,true);
        var review = locked(company,id);
        flow.reviewer(company,admin.getId(),((Number)review.get("employee_id")).longValue());
        employee(company,((Number)review.get("employee_id")).longValue());
        if (((Number)review.get("version")).intValue()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Review changed; reload before publishing");
        if (review.get("published_at") != null) throw new IllegalArgumentException("Review is already published");
        db.update("UPDATE performance_reviews SET published_at=CURRENT_TIMESTAMP,modified_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",id);
        audit(id,admin.getId(),"PUBLISHED");
        emailAlerts.enqueueCompany(company,((Number)review.get("employee_id")).longValue(), EmailAlertService.Category.PERFORMANCE,
            "Chronos: performance review published", "/performance-reviews",id,false);
    }
    private Map<String,Object> locked(long company,UUID id) {
        var rows = db.queryForList("SELECT * FROM performance_reviews WHERE id=? AND company_id=? FOR UPDATE",id,company);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found");
        return rows.getFirst();
    }
    public List<Map<String,Object>> history(long company,String email, UUID id) {
        User actor=actor(company,email,true);
        var review=locked(company,id);flow.reviewer(company,actor.getId(),((Number)review.get("employee_id")).longValue());
        return db.queryForList("SELECT * FROM performance_review_audit WHERE review_id=? ORDER BY id DESC",id);
    }
    private void audit(UUID id, Long actor, String action) {
        db.update("INSERT INTO performance_review_audit(review_id,actor_id,action,snapshot) SELECT id,?,?,to_jsonb(r) FROM performance_reviews r WHERE id=?",actor,action,id);
    }
}
