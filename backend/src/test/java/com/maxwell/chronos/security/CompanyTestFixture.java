package com.maxwell.chronos.security;

import com.maxwell.chronos.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.UUID;

/** Company-scoped integration records are rolled back by each test transaction. */
final class CompanyTestFixture {
    static long company(JdbcTemplate db, User admin, User... members) {
        long id = db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Integration company',?) RETURNING id", Long.class, "integration-" + UUID.randomUUID());
        for (User member : members) db.update("INSERT INTO company_memberships(company_id,user_id,status,workforce_enabled) VALUES (?,?,'ACTIVE',TRUE)", id, member.getId());
        db.update("INSERT INTO role_assignments(company_id,user_id,role_key) VALUES (?,?,'COMPANY_ADMIN')", id, admin.getId());
        return id;
    }
    static void grant(JdbcTemplate db, long company, User user, String permission, Long subject, User authorizer) {
        db.update("INSERT INTO company_sensitive_grants(id,company_id,user_id,permission,subject_user_id,starts_on,ends_on,purpose,granted_by) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(),company, user.getId(), permission, subject, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "Integration fixture", authorizer.getId());
    }
}
