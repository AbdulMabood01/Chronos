package com.maxwell.chronos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailAlertService {
    public enum Category { ANNOUNCEMENTS, TIMESHEETS, VACATION, LETTERS, REPORTS, FEEDBACK, PERFORMANCE }
    public record Preferences(boolean enabled, boolean announcements, boolean timesheets, boolean vacation,
                              boolean letters, boolean reports, boolean feedback, boolean performance) {
        public boolean allows(Category category) {
            return enabled && switch (category) {
                case ANNOUNCEMENTS -> announcements;
                case TIMESHEETS -> timesheets;
                case VACATION -> vacation;
                case LETTERS -> letters;
                case REPORTS -> reports;
                case FEEDBACK -> feedback;
                case PERFORMANCE -> performance;
            };
        }
    }
    private final JdbcTemplate db;

    public Preferences preferences(Long userId) {
        return db.query("SELECT * FROM email_alert_preferences WHERE user_id=?", (rs, row) ->
            new Preferences(rs.getBoolean("enabled"), rs.getBoolean("announcements"), rs.getBoolean("timesheets"),
                rs.getBoolean("vacation"), rs.getBoolean("letters"), rs.getBoolean("reports"),
                rs.getBoolean("feedback"), rs.getBoolean("performance")), userId)
            .stream().findFirst().orElse(new Preferences(true, true, true, true, true, true, true, true));
    }

    public Preferences save(Long userId, Preferences p) {
        db.update("""
            INSERT INTO email_alert_preferences(user_id,enabled,announcements,timesheets,vacation,letters,reports,feedback,performance)
            VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET enabled=EXCLUDED.enabled,
            announcements=EXCLUDED.announcements,timesheets=EXCLUDED.timesheets,vacation=EXCLUDED.vacation,
            letters=EXCLUDED.letters,reports=EXCLUDED.reports,feedback=EXCLUDED.feedback,performance=EXCLUDED.performance
            """, userId,p.enabled(),p.announcements(),p.timesheets(),p.vacation(),p.letters(),p.reports(),p.feedback(),p.performance());
        return p;
    }

    public void enqueue(Long userId, Category category, String subject, String path) {
        if (preferences(userId).allows(category))
            db.update("INSERT INTO email_alert_outbox(user_id,category,subject,path) VALUES (?,?,?,?)",
                userId, category.name(), subject, path);
    }

    public void enqueueCompany(long company,long user,Category category,String subject,String path,java.util.UUID resource,boolean handlerOnly) {
        if(preferences(user).allows(category))db.update("INSERT INTO email_alert_outbox(company_id,user_id,category,subject,path,resource_id,handler_only) VALUES (?,?,?,?,?,?,?)",company,user,category.name(),subject,path,resource,handlerOnly);
    }
    public boolean companyDeliveryAllowed(java.util.Map<String,Object> item) {
        if(!(item.get("company_id") instanceof Number company))return !java.util.Set.of("ANNOUNCEMENTS","REPORTS","FEEDBACK","PERFORMANCE").contains(item.get("category"));
        long user=((Number)item.get("user_id")).longValue();
        boolean member=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=? AND m.user_id=? AND m.status='ACTIVE' AND EXISTS(SELECT 1 FROM companies c WHERE c.id=m.company_id AND NOT c.is_suspended) AND u.is_active AND NOT u.admin_locked AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL))",Boolean.class,company.longValue(),user));
        if(!member)return false;
        if(Boolean.TRUE.equals(item.get("handler_only")))return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_sensitive_grants g JOIN employee_reports r ON r.company_id=g.company_id WHERE g.company_id=? AND g.user_id=? AND g.permission='CONFIDENTIAL_HANDLER' AND g.revoked_at IS NULL AND g.starts_on<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND g.ends_on>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND r.id=? AND (r.reporter_id IS NULL OR r.reporter_id<>?) AND NOT EXISTS(SELECT 1 FROM employee_report_exclusions x WHERE x.report_id=r.id AND x.exclusion_token=encode(sha256(r.recusal_salt||convert_to(?::text,'UTF8')),'hex')))",Boolean.class,company.longValue(),user,item.get("resource_id"),user,user));
        if("ANNOUNCEMENTS".equals(item.get("category")))return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_announcements WHERE id=? AND company_id=? AND status='PUBLISHED' AND publish_date<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND (expiration_date IS NULL OR expiration_date>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date))",Boolean.class,item.get("resource_id"),company.longValue()));
        if("PERFORMANCE".equals(item.get("category")))return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM performance_reviews WHERE id=? AND company_id=? AND employee_id=? AND published_at IS NOT NULL)",Boolean.class,item.get("resource_id"),company.longValue(),user));
        if("FEEDBACK".equals(item.get("category")))return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM employee_feedback WHERE id=? AND company_id=? AND recipient_id=?)",Boolean.class,item.get("resource_id"),company.longValue(),user));
        if("REPORTS".equals(item.get("category")))return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM employee_reports WHERE id=? AND company_id=? AND reporter_id=? AND NOT anonymous)",Boolean.class,item.get("resource_id"),company.longValue(),user));
        return true;
    }

    public void notifyHr(Category category, String subject, String path) {
        db.queryForList("SELECT id FROM users WHERE is_active=true AND role='ADMIN'", Long.class)
            .forEach(id -> enqueue(id, category, subject, path));
    }

    public void notification(Long userId, String type) {
        // Reminders already have their own sender and must not be mailed twice.
        if (type.equals("TIMESHEET_REMINDER") || type.equals("TIMESHEET_LATE")) return;
        Category category;
        if (type.startsWith("TIMESHEET_")) category=Category.TIMESHEETS;
        else if (type.startsWith("VACATION_")) category=Category.VACATION;
        else if (type.startsWith("LETTER_")) category=Category.LETTERS;
        else return;
        // Details remain in the authenticated inbox, rather than in email.
        String action = type.substring(type.lastIndexOf('_') + 1).toLowerCase(Locale.ROOT);
        enqueue(userId, category, "Chronos: " + category.name().toLowerCase(Locale.ROOT) + " " + action, "/notifications");
    }
}
