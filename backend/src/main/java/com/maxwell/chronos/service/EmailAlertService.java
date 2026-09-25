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

    public void notifyHr(Category category, String subject, String path) {
        db.queryForList("SELECT id FROM users WHERE is_active=true AND role='ADMIN'", Long.class)
            .forEach(id -> enqueue(id, category, subject, path));
    }

    public void notification(Long userId, String type) {
        // Reminders already have their own sender and must not be mailed twice.
        if (type.equals("TIMESHEET_REMINDER")) return;
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
