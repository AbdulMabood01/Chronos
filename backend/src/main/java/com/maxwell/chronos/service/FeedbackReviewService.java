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
public class FeedbackReviewService {
    public enum Category { APPRECIATION, GENERAL, COLLABORATION, COMMUNICATION, IMPROVEMENT, OTHER }
    public record Feedback(@NotNull Long employeeId, @NotBlank @Size(max=20000) String content,
                           Category category, boolean anonymous) {}
    public record Review(@NotNull Long employeeId, @Min(1900) @Max(9999) int year,
        @Min(1) @Max(4) int quarter, @NotBlank @Size(max=20000) String summary,
        @Size(max=20000) String accomplishments, @Size(max=20000) String strengths,
        @Size(max=20000) String improvements, @Size(max=20000) String goals,
        @Size(max=20000) String comments, @Min(0) int version) {}
    private final JdbcTemplate db;
    private final UserRepository users;
    private final ProjectRepository projects;

    private User actor(String email, boolean admin) {
        User user = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!Boolean.TRUE.equals(user.getIsActive()) || (admin && !user.isSuperAdmin()))
            throw new AccessDeniedException("Access denied");
        return user;
    }
    private void employee(Long id) {
        if (id == null || !users.findById(id).map(u -> Boolean.TRUE.equals(u.getIsActive())).orElse(false))
            throw new IllegalArgumentException("Select an active employee");
    }
    public List<Map<String,Object>> search(String email, String query) {
        actor(email, false);
        if (query == null || query.trim().length() < 2) return List.of();
        if (query.length() > 200) throw new IllegalArgumentException("Search is too long");
        String term = query.trim().toLowerCase(Locale.ROOT);
        return db.queryForList("""
            SELECT id,first_name,last_name,email FROM users WHERE is_active=true
            AND (strpos(lower(first_name || ' ' || last_name),?)>0 OR strpos(lower(email),?)>0)
            ORDER BY first_name,last_name,id LIMIT 30
            """, term, term);
    }
    public UUID submit(String email, Feedback input) {
        User sender = actor(email, false);
        employee(input.employeeId());
        if (sender.getId().equals(input.employeeId())) throw new IllegalArgumentException("Choose another employee");
        UUID id = UUID.randomUUID();
        String type = sender.isAdmin() || sender.isSuperAdmin() || projects.existsByProjectManagerIdOrProjectManagerHoursApproverId(sender.getId(), sender.getId()) ? "Manager" : "Employee";
        db.update("INSERT INTO employee_feedback(id,sender_id,recipient_id,content,category,anonymous,sender_type) VALUES (?,?,?,?,?,?,?)",
            id, sender.getId(), input.employeeId(), input.content().trim(), input.category() == null ? null : input.category().name(), input.anonymous(), type);
        return id;
    }
    public List<Map<String,Object>> feedback(String email, boolean given) {
        User user = actor(email, false);
        // Recipient projection never selects sender identifiers, including for anonymous records.
        String projection = given
            ? "u.first_name || ' ' || u.last_name AS recipient_name,u.email AS recipient_email"
            : "CASE WHEN f.anonymous THEN NULL ELSE u.first_name || ' ' || u.last_name END AS sender_name";
        return db.queryForList("SELECT f.id,f.content,f.category,f.anonymous,f.sender_type,f.submitted_at," + projection
            + " FROM employee_feedback f JOIN users u ON u.id=f." + (given ? "recipient_id" : "sender_id")
            + " WHERE f." + (given ? "sender_id" : "recipient_id") + "=? ORDER BY f.submitted_at DESC,f.id", user.getId());
    }
    public List<Map<String,Object>> reviews(String email, Long employeeId) {
        User user = actor(email, false);
        if (!user.isSuperAdmin() && employeeId != null && !employeeId.equals(user.getId())) throw new AccessDeniedException("Access denied");
        if (user.isSuperAdmin() && employeeId == null) {
            return db.queryForList("SELECT r.*,u.first_name,u.last_name,u.email AS employee_email,u.first_name || ' ' || u.last_name AS employee_name FROM performance_reviews r JOIN users u ON u.id=r.employee_id ORDER BY r.review_year DESC,r.quarter DESC,r.modified_at DESC,r.id");
        }
        Long target = employeeId == null ? user.getId() : employeeId;
        return db.queryForList("SELECT r.*,u.first_name || ' ' || u.last_name AS employee_name FROM performance_reviews r JOIN users u ON u.id=r.employee_id WHERE r.employee_id=?"
            + (user.isSuperAdmin() ? "" : " AND r.published_at IS NOT NULL")
            + " ORDER BY r.published_at DESC NULLS LAST,r.review_year DESC,r.quarter DESC", target);
    }
    public UUID save(String email, UUID id, Review input) {
        User admin = actor(email, true);
        employee(input.employeeId());
        if (id == null) {
            id = UUID.randomUUID();
            db.update("""
                INSERT INTO performance_reviews(id,employee_id,review_year,quarter,summary,accomplishments,strengths,improvements,goals,comments,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id,input.employeeId(),input.year(),input.quarter(),input.summary().trim(),input.accomplishments(),input.strengths(),input.improvements(),input.goals(),input.comments(),admin.getId());
            audit(id,admin.getId(),"CREATED");
        } else {
            var previous = locked(id);
            if (!Objects.equals(previous.get("employee_id"),input.employeeId()) || ((Number)previous.get("review_year")).intValue()!=input.year() || ((Number)previous.get("quarter")).intValue()!=input.quarter())
                throw new IllegalArgumentException("Employee and review period cannot be changed");
            if (((Number)previous.get("version")).intValue()!=input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Review changed; reload before saving");
            db.update("UPDATE performance_reviews SET summary=?,accomplishments=?,strengths=?,improvements=?,goals=?,comments=?,modified_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
                input.summary().trim(),input.accomplishments(),input.strengths(),input.improvements(),input.goals(),input.comments(),id);
            audit(id,admin.getId(),"EDITED");
        }
        return id;
    }
    public void publish(String email, UUID id, int version) {
        User admin = actor(email,true);
        var review = locked(id);
        if (((Number)review.get("version")).intValue()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Review changed; reload before publishing");
        if (review.get("published_at") != null) throw new IllegalArgumentException("Review is already published");
        db.update("UPDATE performance_reviews SET published_at=CURRENT_TIMESTAMP,modified_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",id);
        audit(id,admin.getId(),"PUBLISHED");
    }
    private Map<String,Object> locked(UUID id) {
        var rows = db.queryForList("SELECT * FROM performance_reviews WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found");
        return rows.getFirst();
    }
    public List<Map<String,Object>> history(String email, UUID id) {
        actor(email,true);
        return db.queryForList("SELECT * FROM performance_review_audit WHERE review_id=? ORDER BY id DESC",id);
    }
    private void audit(UUID id, Long actor, String action) {
        db.update("INSERT INTO performance_review_audit(review_id,actor_id,action,snapshot) SELECT id,?,?,to_jsonb(r) FROM performance_reviews r WHERE id=?",actor,action,id);
    }
}
