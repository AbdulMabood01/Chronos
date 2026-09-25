package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeReportService {
    public enum Category { SEXUAL_HARASSMENT, WORKPLACE_HARASSMENT, DISCRIMINATION, SAFETY_CONCERN, WORKPLACE_MISCONDUCT, OTHER_INCIDENT }
    public enum Status { SUBMITTED, UNDER_REVIEW, INVESTIGATION, RESOLVED, CLOSED }
    public record Submission(@NotNull Category category, @NotBlank @Size(max=200) String subject,
        @NotBlank @Size(max=20000) String description, LocalDateTime incidentAt,
        @Size(max=500) String location, @Size(max=5000) String peopleInvolved,
        @Size(max=5000) String witnesses, boolean anonymous, @AssertTrue boolean privacyAcknowledged) {
        @Override public String toString() { return "Confidential report submission [REDACTED]"; }
    }
    public record Review(@NotNull Status status, @Size(max=10000) String note,
        @Size(max=10000) String actionsTaken, @Size(max=10000) String resolution, boolean shareWithEmployee) {
        public Review(Status status, String note, String actionsTaken, String resolution) { this(status, note, actionsTaken, resolution, false); }
        @Override public String toString() { return "Confidential report review [REDACTED]"; }
    }
    public record Receipt(UUID reportId, String status) {}
    public record Download(String filename, byte[] content) {}
    private final JdbcTemplate db;
    private final UserRepository users;
    private final EmailAlertService emailAlerts;

    private User requireUser(String email, boolean admin) {
        User user = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!Boolean.TRUE.equals(user.getIsActive()) || (admin && !user.isAdmin()))
            throw new AccessDeniedException("Only HR Admins can access employee reports");
        return user;
    }

    public Receipt submit(String email, Submission input, List<MultipartFile> files) {
        User user = requireUser(email, false);
        if (!input.privacyAcknowledged()) throw new IllegalArgumentException("Read and acknowledge the privacy notice");
        if (files == null) files = List.of();
        if (files.size() > 5 || files.stream().mapToLong(MultipartFile::getSize).sum() > 20L * 1024 * 1024)
            throw new IllegalArgumentException("Attach up to 5 files, 20 MB total");
        // Validate all files before persisting any report data.
        List<Download> attachments = files.stream().map(this::validateFile)
            .map(file -> input.anonymous() ? AnonymousReportAttachment.sanitize(file.filename(), file.content(), UUID.randomUUID().toString()) : file).toList();
        if (attachments.stream().mapToLong(file -> file.content().length).sum() > 20L * 1024 * 1024)
            throw new IllegalArgumentException("Processed attachments exceed 20 MB. Use smaller files.");
        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO employee_reports(id,category,subject,description,incident_at,location,people_involved,witnesses,anonymous,reporter_id)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, id, input.category().name(), input.subject().trim(), input.description().trim(), input.incidentAt(),
            input.location(), input.peopleInvolved(), input.witnesses(), input.anonymous(), input.anonymous() ? null : user.getId());
        for (Download file : attachments)
            db.update("INSERT INTO employee_report_attachments(id,report_id,filename,content,privacy_processed) VALUES (?,?,?,?,?)", UUID.randomUUID(), id, file.filename(), file.content(), input.anonymous());
        // Never record the submitter in an audit event, including for anonymous submissions.
        history(id, null, "SUBMITTED", null, null, null);
        emailAlerts.notifyHr(EmailAlertService.Category.REPORTS, "Chronos: report submitted", "/reports");
        return new Receipt(id, "SUBMITTED");
    }

    private Download validateFile(MultipartFile file) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("attachment").replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 200 || file.isEmpty() || file.getSize() > 10L * 1024 * 1024)
            throw new IllegalArgumentException("Each attachment must be nonempty, up to 10 MB, with a filename under 200 characters");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("png","jpg","jpeg","gif","webp","pdf","doc","docx","txt","odt").contains(extension))
            throw new IllegalArgumentException("Supported attachments: PNG, JPG, GIF, WebP, PDF, DOC, DOCX, TXT, ODT");
        try { return new Download(name, file.getBytes()); }
        catch (IOException e) { throw new IllegalArgumentException("Unable to read attachment"); }
    }

    public List<Map<String,Object>> list(String email, Category category, Status status, LocalDate from, LocalDate to, int page) {
        return list(email, category, status, from, to, page, null, null);
    }

    public List<Map<String,Object>> list(String email, Category category, Status status, LocalDate from, LocalDate to, int page, String reportId, Boolean anonymous) {
        requireUser(email, true);
        if (page < 0 || page > 100000 || (from != null && to != null && from.isAfter(to)))
            throw new IllegalArgumentException("Invalid date range or page");
        StringBuilder sql = new StringBuilder("""
            SELECT r.id,r.category,r.subject,r.status,r.anonymous,r.submitted_at,r.updated_at,
                CASE WHEN r.anonymous THEN 'Anonymous' ELSE concat_ws(' ',u.first_name,u.last_name) END AS submitted_by
            FROM employee_reports r LEFT JOIN users u ON u.id=r.reporter_id AND NOT r.anonymous WHERE 1=1
            """);
        List<Object> args = new ArrayList<>();
        if (category != null) { sql.append(" AND r.category=?"); args.add(category.name()); }
        if (status != null) { sql.append(" AND r.status=?"); args.add(status.name()); }
        if (from != null) { sql.append(" AND r.submitted_at>=?"); args.add(from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)); }
        if (to != null) { sql.append(" AND r.submitted_at<?"); args.add(to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)); }
        if (reportId != null && !reportId.isBlank()) {
            if (!reportId.trim().matches("[a-fA-F0-9-]{1,36}")) throw new IllegalArgumentException("Enter a Report ID or part of an ID");
            sql.append(" AND CAST(r.id AS text) LIKE ?"); args.add("%" + reportId.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (anonymous != null) { sql.append(" AND r.anonymous=?"); args.add(anonymous); }
        sql.append(" ORDER BY r.submitted_at DESC,r.id LIMIT 50 OFFSET ?"); args.add(page * 50);
        return db.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String,Object>> mine(String email, int page) {
        User user = requireUser(email, false);
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
        return db.queryForList("SELECT id,category,subject,status,submitted_at,updated_at FROM employee_reports WHERE reporter_id=? AND NOT anonymous ORDER BY submitted_at DESC,id LIMIT 50 OFFSET ?", user.getId(), page * 50);
    }

    public Map<String,Object> myDetail(String email, UUID id) {
        User user = requireUser(email, false);
        var rows = db.queryForList("SELECT id,category,subject,description,incident_at,location,people_involved,witnesses,status,submitted_at,updated_at FROM employee_reports WHERE id=? AND reporter_id=? AND NOT anonymous", id, user.getId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        var result = new HashMap<>(rows.getFirst());
        result.put("history", db.queryForList("""
            SELECT id,status,created_at,
                CASE WHEN employee_visible THEN actions_taken END AS actions_taken,
                CASE WHEN employee_visible THEN resolution END AS resolution
            FROM employee_report_history WHERE report_id=? AND action NOT IN ('VIEWED','ATTACHMENT_DOWNLOADED')
            AND (action <> 'NOTE_ADDED' OR employee_visible) ORDER BY id
            """, id));
        return result;
    }

    private Map<String,Object> report(UUID id, boolean lock) {
        var rows = db.queryForList("SELECT * FROM employee_reports WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        return rows.getFirst();
    }

    public Map<String,Object> detail(String email, UUID id) {
        User admin = requireUser(email, true);
        Map<String,Object> result = report(id, false);
        Object reporter = result.remove("reporter_id");
        if (!Boolean.TRUE.equals(result.get("anonymous")) && reporter != null)
            result.put("reporter", db.queryForList("SELECT first_name,last_name,email FROM users WHERE id=?", reporter).stream().findFirst().orElse(null));
        var attachments = db.queryForList("SELECT id,filename,octet_length(content) AS size FROM employee_report_attachments WHERE report_id=?", id);
        if (Boolean.TRUE.equals(result.get("anonymous"))) for (var file : attachments) {
            String name = (String)file.get("filename");
            boolean supported = AnonymousReportAttachment.supported(name);
            file.put("filename", "attachment-" + file.get("id") + (supported ? "." + (Set.of("jpg","jpeg","gif").contains(AnonymousReportAttachment.extension(name)) ? "png" : AnonymousReportAttachment.extension(name)) : ""));
            file.put("downloadAvailable", supported);
        }
        result.put("attachments", attachments);
        history(id, admin.getId(), "VIEWED", null, null, null);
        result.put("history", db.queryForList("""
            SELECT h.id,h.action,h.status,h.employee_visible,h.note,h.actions_taken,h.resolution,h.created_at,u.first_name,u.last_name
            FROM employee_report_history h LEFT JOIN users u ON u.id=h.actor_id WHERE report_id=? ORDER BY h.id
            """, id));
        return result;
    }

    public void review(String email, UUID id, Review input) {
        User admin = requireUser(email, true);
        var report = report(id, true);
        Status previous = Status.valueOf((String)report.get("status"));
        if (input.status() != previous && input.status().ordinal() != previous.ordinal() + 1)
            throw new IllegalArgumentException("Move reports forward one status at a time");
        if (previous == Status.CLOSED) throw new IllegalArgumentException("Closed reports cannot be changed");
        if (input.status() == Status.RESOLVED && previous != Status.RESOLVED && (blank(input.resolution()) || blank(input.actionsTaken())))
            throw new IllegalArgumentException("Record actions taken and resolution details before resolving");
        if (input.status() == previous && blank(input.note()) && blank(input.actionsTaken()) && blank(input.resolution()))
            throw new IllegalArgumentException("Add a note, action, resolution, or status change");
        db.update("UPDATE employee_reports SET status=?,updated_at=clock_timestamp() WHERE id=?", input.status().name(), id);
        emailAlerts.notifyHr(EmailAlertService.Category.REPORTS, "Chronos: report updated", "/reports");
        if (!Boolean.TRUE.equals(report.get("anonymous")) && report.get("reporter_id") instanceof Number reporter)
            emailAlerts.enqueue(reporter.longValue(), EmailAlertService.Category.REPORTS,
                "Chronos: report " + id + " is " + input.status().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), "/workplace-reports");
        history(id, admin.getId(), previous == input.status() ? "NOTE_ADDED" : previous + " → " + input.status(), input.note(), input.actionsTaken(), input.resolution(), input.shareWithEmployee());
    }

    public Download download(String email, UUID id, UUID attachmentId) {
        User admin = requireUser(email, true);
        var rows = db.queryForList("SELECT a.filename,a.content,a.privacy_processed,r.anonymous FROM employee_report_attachments a JOIN employee_reports r ON r.id=a.report_id WHERE a.id=? AND a.report_id=?", attachmentId, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        var file = rows.getFirst();
        Download download = new Download((String)file.get("filename"), (byte[])file.get("content"));
        if (Boolean.TRUE.equals(file.get("anonymous"))) {
            if (Boolean.TRUE.equals(file.get("privacy_processed")))
                download = new Download("attachment-" + attachmentId + "." + AnonymousReportAttachment.extension(download.filename()), download.content());
            else download = AnonymousReportAttachment.sanitize(download.filename(), download.content(), attachmentId.toString());
        }
        history(id, admin.getId(), "ATTACHMENT_DOWNLOADED", attachmentId.toString(), null, null);
        return download;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void history(UUID id, Long actor, String action, String note, String actions, String resolution, boolean shared) {
        if (!shared) { history(id, actor, action, note, actions, resolution); return; }
        db.update("INSERT INTO employee_report_history(report_id,actor_id,action,note,actions_taken,resolution,status,employee_visible) VALUES (?,?,?,?,?,?,(SELECT status FROM employee_reports WHERE id=?),true)", id, actor, action, note, actions, resolution, id);
    }
    private void history(UUID id, Long actor, String action, String note, String actions, String resolution) {
        db.update("INSERT INTO employee_report_history(report_id,actor_id,action,note,actions_taken,resolution,status) VALUES (?,?,?,?,?,?,(SELECT status FROM employee_reports WHERE id=?))", id, actor, action, note, actions, resolution, id);
    }
}
