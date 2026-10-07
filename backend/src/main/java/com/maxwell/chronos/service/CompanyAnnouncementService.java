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
public class CompanyAnnouncementService {
    public enum Priority { NORMAL, IMPORTANT, URGENT }
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public record Input(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=50000) String content,
        @NotNull LocalDate publishDate, LocalDate expirationDate, @NotNull Priority priority,
        @NotNull Status status, boolean acknowledgmentRequired, @Min(0) int version,
        @Size(max=200) String attachmentName, @Size(max=6990508) String attachmentBase64, boolean removeAttachment) {}
    private final JdbcTemplate db;
    private final CompanyWorkflowAccess flow;
    private static final String VISIBLE = "a.status='PUBLISHED' AND a.publish_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND (a.expiration_date IS NULL OR a.expiration_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date)";
    private static final String FIELDS = "a.id,a.title,a.content,a.publish_date,a.expiration_date,a.priority,a.status,a.acknowledgment_required,a.attachment_name,a.version,a.updated_at";

    private User actor(long company,String email, boolean admin) { return flow.lockMember(company,email,admin); }
    public List<Map<String,Object>> list(long company,String email, boolean management) {
        User u = actor(company,email,management);
        return db.queryForList("SELECT " + FIELDS + ",r.viewed_at,r.acknowledged_at FROM company_announcements a LEFT JOIN announcement_receipts r ON r.announcement_id=a.id AND r.employee_id=?"
            + " WHERE a.company_id=?" + (management ? "" : " AND " + VISIBLE) + " ORDER BY a.publish_date DESC,a.updated_at DESC,a.id",u.getId(),company);
    }
    private Map<String,Object> accessible(long company,UUID id, boolean management) {
        var rows = db.queryForList("SELECT " + FIELDS + " FROM company_announcements a WHERE a.id=? AND a.company_id=?" + (management ? "" : " AND " + VISIBLE) + " FOR UPDATE",id,company);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Announcement not found");
        return rows.getFirst();
    }
    public Map<String,Object> open(long company,String email, UUID id, boolean management) {
        User u = actor(company,email,management);
        var row = accessible(company,id,management);
        if (!management) db.update("INSERT INTO announcement_receipts(announcement_id,employee_id) VALUES (?,?) ON CONFLICT DO NOTHING",id,u.getId());
        var receipt = db.queryForList("SELECT viewed_at,acknowledged_at FROM announcement_receipts WHERE announcement_id=? AND employee_id=?",id,u.getId());
        if (!receipt.isEmpty()) row.putAll(receipt.getFirst());
        return row;
    }
    public void acknowledge(long company,String email, UUID id, int version) {
        User u = actor(company,email,false);
        var row = accessible(company,id,false);
        checkVersion(row,version);
        if (!Boolean.TRUE.equals(row.get("acknowledgment_required"))) throw new IllegalArgumentException("Acknowledgment is not required");
        db.update("INSERT INTO announcement_receipts(announcement_id,employee_id,acknowledged_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT (announcement_id,employee_id) DO UPDATE SET acknowledged_at=COALESCE(announcement_receipts.acknowledged_at,EXCLUDED.acknowledged_at)",id,u.getId());
    }
    private void checkVersion(Map<String,Object> row, int version) {
        if (((Number)row.get("version")).intValue()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Announcement changed; reopen it before continuing");
    }
    public UUID save(long company,String email, UUID id, Input in) {
        User u = actor(company,email,true);
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
            db.update("INSERT INTO company_announcements(company_id,id,title,content,publish_date,expiration_date,priority,status,acknowledgment_required,attachment_name,attachment_data,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                company,id,in.title().trim(),in.content().trim(),in.publishDate(),in.expirationDate(),in.priority().name(),in.status().name(),in.acknowledgmentRequired(),bytes==null?null:in.attachmentName(),bytes,u.getId());
        } else {
            checkVersion(accessible(company,id,true),in.version());
            db.update("UPDATE company_announcements SET title=?,content=?,publish_date=?,expiration_date=?,priority=?,status=?,acknowledgment_required=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                in.title().trim(),in.content().trim(),in.publishDate(),in.expirationDate(),in.priority().name(),in.status().name(),in.acknowledgmentRequired(),id);
            if (bytes!=null || in.removeAttachment()) db.update("UPDATE company_announcements SET attachment_name=?,attachment_data=? WHERE id=?",bytes==null?null:in.attachmentName(),bytes,id);
            db.update("DELETE FROM announcement_receipts WHERE announcement_id=?",id);
        }
        flow.activity(company,u.getId(),"ANNOUNCEMENT_SAVED","Announcement",id);
        return id;
    }
    public void status(long company,String email, UUID id, Status status, int version) {
        User actor=actor(company,email,true); checkVersion(accessible(company,id,true),version);
        db.update("UPDATE company_announcements SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",status.name(),id);flow.activity(company,actor.getId(),"ANNOUNCEMENT_STATUS_CHANGED","Announcement",id);
    }
    public void delete(long company,String email, UUID id, int version) {
        User actor=actor(company,email,true); checkVersion(accessible(company,id,true),version);
        db.update("DELETE FROM company_announcements WHERE id=?",id);flow.activity(company,actor.getId(),"ANNOUNCEMENT_DELETED","Announcement",id);
    }
    public Map<String,Object> tracking(long company,String email, UUID id) {
        actor(company,email,true); accessible(company,id,true);
        // Active accounts define the current employee audience, including administrators.
        var employees = db.queryForList("SELECT u.id,u.first_name || ' ' || u.last_name AS name,u.email,r.viewed_at,r.acknowledged_at FROM users u JOIN company_memberships m ON m.user_id=u.id AND m.company_id=? AND m.status='ACTIVE' LEFT JOIN announcement_receipts r ON r.employee_id=u.id AND r.announcement_id=? WHERE u.is_active=true AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL) ORDER BY u.first_name,u.last_name,u.id",company,id);
        return Map.of("total",employees.size(),"viewed",employees.stream().filter(e -> e.get("viewed_at")!=null).count(),
            "acknowledged",employees.stream().filter(e -> e.get("acknowledged_at")!=null).count(),"employees",employees);
    }
    public Map<String,Object> attachment(long company,String email, UUID id, boolean management) {
        actor(company,email,management); accessible(company,id,management);
        var rows=db.queryForList("SELECT attachment_name,attachment_data FROM company_announcements WHERE id=? AND attachment_data IS NOT NULL",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attachment not found");
        return rows.getFirst();
    }
}
