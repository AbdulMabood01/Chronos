package com.maxwell.chronos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="chronos.email-alerts.enabled", matchIfMissing=true)
public class EmailAlertDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailAlertDispatcher.class);
    private final JdbcTemplate db;
    private final EmailAlertService alerts;
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final String appUrl;

    public EmailAlertDispatcher(JdbcTemplate db, EmailAlertService alerts, ObjectProvider<JavaMailSender> sender,
            @Value("${chronos.mail.from:}") String from,
            @Value("${chronos.app-url:http://localhost:5173}") String appUrl) {
        this.db=db; this.alerts=alerts; this.sender=sender; this.from=from;
        this.appUrl=appUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString="${chronos.email-alerts.poll-ms:30000}")
    @Transactional
    public void deliver() {
        // One dispatcher transaction at a time, including across application instances.
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('chronos-company-alert-delivery',0))",Object.class);
        // Includes scheduled announcements once their publication date arrives.
        var announcements = db.queryForList("""
            SELECT id,version,company_id FROM company_announcements WHERE status='PUBLISHED' AND company_id IS NOT NULL
            AND publish_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
            AND (expiration_date IS NULL OR expiration_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date)
            AND emailed_version < version ORDER BY company_id,id
            """);
        for (var announcement : announcements) {
            long company=((Number)announcement.get("company_id")).longValue();
            db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,company);
            var current=db.queryForList("SELECT id FROM company_announcements WHERE id=? AND status='PUBLISHED' AND emailed_version<version AND publish_date<=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AND (expiration_date IS NULL OR expiration_date>=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date) FOR UPDATE",announcement.get("id"));
            if(current.isEmpty())continue;
            db.queryForList("SELECT u.id FROM users u JOIN company_memberships m ON m.user_id=u.id WHERE m.company_id=? AND m.status='ACTIVE' AND u.is_active AND NOT u.admin_locked AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL)",Long.class,company).forEach(id ->
                alerts.enqueueCompany(company,id, EmailAlertService.Category.ANNOUNCEMENTS, "Chronos: announcement published or updated", "/announcements",(java.util.UUID)announcement.get("id"),false));
            db.update("UPDATE company_announcements SET emailed_version=version WHERE id=?", announcement.get("id"));
        }
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null || from.isBlank()) return;
        var pending = db.queryForList("""
            SELECT o.*,u.email,u.is_active FROM email_alert_outbox o JOIN users u ON u.id=o.user_id
            WHERE o.completed_at IS NULL AND o.available_at <= CURRENT_TIMESTAMP AND o.attempts < 5
            ORDER BY o.id LIMIT 25 FOR UPDATE OF o SKIP LOCKED
            """);
        for (var item : pending) {
            if(item.get("company_id") instanceof Number company)db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,company.longValue());
            Long userId = ((Number)item.get("user_id")).longValue();
            var category = EmailAlertService.Category.valueOf((String)item.get("category"));
            if (!Boolean.TRUE.equals(item.get("is_active")) || !alerts.preferences(userId).allows(category) || !alerts.companyDeliveryAllowed(item)) {
                db.update("UPDATE email_alert_outbox SET completed_at=CURRENT_TIMESTAMP WHERE id=?",item.get("id"));
                continue;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo((String)item.get("email"));
            message.setSubject((String)item.get("subject"));
            message.setText(message.getSubject() + "\n\nOpen Chronos: " + appUrl + item.get("path")
                + "\n\nManage email alerts using the Email alerts button beside the theme control in Chronos.");
            try {
                mail.send(message);
                db.update("UPDATE email_alert_outbox SET completed_at=CURRENT_TIMESTAMP WHERE id=?",item.get("id"));
            } catch (org.springframework.mail.MailException ex) {
                db.update("UPDATE email_alert_outbox SET attempts=attempts+1,available_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes' WHERE id=?",item.get("id"));
                log.warn("Email alert {} delivery failed; attempt {} of 5",item.get("id"),((Number)item.get("attempts")).intValue()+1);
            }
        }
    }
}
