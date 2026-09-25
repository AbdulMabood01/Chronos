package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementService {
    public enum Priority { NORMAL, IMPORTANT, URGENT }
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public record Input(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=50000) String content,
        @NotNull LocalDate publishDate, LocalDate expirationDate, @NotNull Priority priority,
        @NotNull Status status, boolean acknowledgmentRequired, @Min(0) int version,
        @Size(max=200) String attachmentName, @Size(max=6990508) String attachmentBase64, boolean removeAttachment) {}
    private final JdbcTemplate db;
    private final UserRepository users;
    private static final String VISIBLE = "a.status='PUBLISHED' AND a.publish_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND (a.expiration_date IS NULL OR a.expiration_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date)";
    private static final String FIELDS = "a.id,a.title,a.content,a.publish_date,a.expiration_date,a.priority,a.status,a.acknowledgment_required,a.attachment_name,a.version,a.updated_at";

    private User actor(String email, boolean admin) {
        User u = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!Boolean.TRUE.equals(u.getIsActive()) || (admin && !u.isAdmin())) throw new AccessDeniedException("Access denied");
        return u;
    }
    public List<Map<String,Object>> list(String email, boolean management) {
        User u = actor(email,management);
        return db.queryForList("SELECT " + FIELDS + ",r.viewed_at,r.acknowledged_at FROM company_announcements a LEFT JOIN announcement_receipts r ON r.announcement_id=a.id AND r.employee_id=?"
            + (management ? "" : " WHERE " + VISIBLE) + " ORDER BY a.publish_date DESC,a.updated_at DESC,a.id",u.getId());
    }
    private Map<String,Object> accessible(UUID id, boolean management) {
        var rows = db.queryForList("SELECT " + FIELDS + " FROM company_announcements a WHERE a.id=?" + (management ? "" : " AND " + VISIBLE) + " FOR UPDATE",id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Announcement not found");
        return rows.getFirst();
    }
    public Map<String,Object> open(String email, UUID id, boolean management) {
        User u = actor(email,management);
        var row = accessible(id,management);
        if (!management) db.update("INSERT INTO announcement_receipts(announcement_id,employee_id) VALUES (?,?) ON CONFLICT DO NOTHING",id,u.getId());
        var receipt = db.queryForList("SELECT viewed_at,acknowledged_at FROM announcement_receipts WHERE announcement_id=? AND employee_id=?",id,u.getId());
        if (!receipt.isEmpty()) row.putAll(receipt.getFirst());
        return row;
    }
    public void acknowledge(String email, UUID id, int version) {
        User u = actor(email,false);
        var row = accessible(id,false);
        checkVersion(row,version);
        if (!Boolean.TRUE.equals(row.get("acknowledgment_required"))) throw new IllegalArgumentException("Acknowledgment is not required");
        db.update("INSERT INTO announcement_receipts(announcement_id,employee_id,acknowledged_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT (announcement_id,employee_id) DO UPDATE SET acknowledged_at=COALESCE(announcement_receipts.acknowledged_at,EXCLUDED.acknowledged_at)",id,u.getId());
    }
    private void checkVersion(Map<String,Object> row, int version) {
        if (((Number)row.get("version")).intValue()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Announcement changed; reopen it before continuing");
    }
    public UUID save(String email, UUID id, Input in) {
        User u = actor(email,true);
        if (in.expirationDate()!=null && in.expirationDate().isBefore(in.publishDate())) throw new IllegalArgumentException("Expiration date must be on or after publish date");
        byte[] bytes = null;
        if (in.attachmentBase64()!=null) {
            try { bytes = Base64.getDecoder().decode(in.attachmentBase64()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid attachment"); }
            if (bytes.length==0 || bytes.length>5*1024*1024 || in.attachmentName()==null || in.attachmentName().isBlank()
                || in.attachmentName().matches(".*[\\\\/\\p{Cntrl}].*")) throw new IllegalArgumentException("Provide a valid attachment up to 5 MB");
        }
        if (id==null) {
            id=UUID.randomUUID();
            db.update("INSERT INTO company_announcements(id,title,content,publish_date,expiration_date,priority,status,acknowledgment_required,attachment_name,attachment_data,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id,in.title().trim(),in.content().trim(),in.publishDate(),in.expirationDate(),in.priority().name(),in.status().name(),in.acknowledgmentRequired(),bytes==null?null:in.attachmentName(),bytes,u.getId());
        } else {
            checkVersion(accessible(id,true),in.version());
            db.update("UPDATE company_announcements SET title=?,content=?,publish_date=?,expiration_date=?,priority=?,status=?,acknowledgment_required=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                in.title().trim(),in.content().trim(),in.publishDate(),in.expirationDate(),in.priority().name(),in.status().name(),in.acknowledgmentRequired(),id);
            if (bytes!=null || in.removeAttachment()) db.update("UPDATE company_announcements SET attachment_name=?,attachment_data=? WHERE id=?",bytes==null?null:in.attachmentName(),bytes,id);
            db.update("DELETE FROM announcement_receipts WHERE announcement_id=?",id);
        }
        return id;
    }
    public void status(String email, UUID id, Status status, int version) {
        actor(email,true); checkVersion(accessible(id,true),version);
        db.update("UPDATE company_announcements SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",status.name(),id);
    }
    public void delete(String email, UUID id, int version) {
        actor(email,true); checkVersion(accessible(id,true),version);
        db.update("DELETE FROM company_announcements WHERE id=?",id);
    }
    public Map<String,Object> tracking(String email, UUID id) {
        actor(email,true); accessible(id,true);
        // Active accounts define the current employee audience, including administrators.
        var employees = db.queryForList("SELECT u.id,u.first_name || ' ' || u.last_name AS name,u.email,r.viewed_at,r.acknowledged_at FROM users u LEFT JOIN announcement_receipts r ON r.employee_id=u.id AND r.announcement_id=? WHERE u.is_active=true ORDER BY u.first_name,u.last_name,u.id",id);
        return Map.of("total",employees.size(),"viewed",employees.stream().filter(e -> e.get("viewed_at")!=null).count(),
            "acknowledged",employees.stream().filter(e -> e.get("acknowledged_at")!=null).count(),"employees",employees);
    }
    public Map<String,Object> attachment(String email, UUID id, boolean management) {
        actor(email,management); accessible(id,management);
        var rows=db.queryForList("SELECT attachment_name,attachment_data FROM company_announcements WHERE id=? AND attachment_data IS NOT NULL",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attachment not found");
        return rows.getFirst();
    }
}
