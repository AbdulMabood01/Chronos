package com.maxwell.chronos.service;

import com.maxwell.chronos.dto.ScopedPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="chronos.localDbTest", matches="true")
class ScopedPermissionsLocalDbTest {
    final DriverManagerDataSource datasource = new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev", "chronos_user", "chronos_password");
    final JdbcTemplate db = new JdbcTemplate(datasource);
    final CompanyAccessService access = new CompanyAccessService(db);

    class Fixture {
        long user, a, b, a1, a2, b1;
        Fixture() {
            String key = "perm-" + UUID.randomUUID();
            // Legacy ADMIN is deliberately present: only scoped assignments may grant the new permissions.
            user = db.queryForObject("INSERT INTO users(employee_id,first_name,last_name,email,role) VALUES (?,'Permission','Test',?,'ADMIN') RETURNING id", Long.class,key,key+"@example.com");
            a = db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Permission A',?) RETURNING id",Long.class,key+"-a");
            b = db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Permission B',?) RETURNING id",Long.class,key+"-b");
            a1 = project(a,"A1"); a2 = project(a,"A2"); b1 = project(b,"B1");
            db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE'),(?,?,'ACTIVE')",a,user,b,user);
        }
        long project(long company, String code) {
            return db.queryForObject("INSERT INTO projects(company_id,code,name,status) VALUES (?,?,?,'ACTIVE') RETURNING id",Long.class,company,code,code);
        }
        void role(long company, Long project, String role) {
            db.update("INSERT INTO role_assignments(user_id,company_id,project_id,role_key) VALUES (?,?,?,?)",user,company,project,role);
            if(project != null) db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE') ON CONFLICT DO NOTHING",project,user);
        }
        void grant(long company,long project,boolean time,boolean expense,String starts,String ends) {
            db.update("INSERT INTO moderator_grants(company_id,project_id,moderator_user_id,timesheets,expenses,starts_on,ends_on,granted_by_user_id) VALUES (?,?,?,?,?,?::date,?::date,?)",
                    company,project,user,time,expense,starts,ends,user);
        }
        ScopedPermissions.Company permissions(long company) { return access.companyPermissions(company,user); }
        ScopedPermissions.Project projectPermissions(long company,long project) {
            return permissions(company).projects().stream().filter(p->p.projectId()==project).findFirst().orElseThrow();
        }
    }
    void run(Consumer<Fixture> test) {
        new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status -> {
            status.setRollbackOnly(); test.accept(new Fixture());
        });
    }

    @Test void companyAdminInAIsOnlyAUserInB() {
        run(f -> {
            f.role(f.a,null,"COMPANY_ADMIN"); f.role(f.b,f.b1,"USER");
            assertTrue(f.permissions(f.a).capabilities().get("canManageCompanyPeople"));
            assertTrue(f.permissions(f.a).capabilities().get("canManageCompanySettings"));
            assertTrue(f.permissions(f.a).capabilities().get("canCreateProjects"));
            assertFalse(f.permissions(f.a).capabilities().get("canReviewWork"));
            assertFalse(f.permissions(f.b).capabilities().get("canManageCompanyPeople"));
            assertFalse(f.permissions(f.b).capabilities().get("canCreateProjects"));
            assertTrue(f.projectPermissions(f.b,f.b1).capabilities().get("canSubmitWork"));
            assertThrows(AccessDeniedException.class, () -> access.requireCompanyCapability(f.b,f.user,"canManageCompanyPeople"));
            assertThrows(AccessDeniedException.class, () -> access.requireCompanyCapability(f.a,f.user,"unrecognizedCapability"));
        });
    }

    @Test void legacyAdminAloneCannotGrantAnyScopedPrivileges() {
        run(f -> {
            assertTrue(f.permissions(f.a).companyRoles().isEmpty());
            assertTrue(f.permissions(f.a).capabilities().values().stream().noneMatch(Boolean.TRUE::equals));
            assertTrue(access.platformPermissions(f.user).capabilities().values().stream().noneMatch(Boolean.TRUE::equals));
        });
    }

    @Test void companyProjectAdminCreatesButDoesNotManageExistingProjects() {
        run(f -> {
            f.role(f.a,null,"PROJECT_ADMIN");
            assertTrue(f.permissions(f.a).capabilities().get("canCreateProjects"));
            assertEquals(2,f.permissions(f.a).projects().size());
            assertFalse(f.projectPermissions(f.a,f.a1).capabilities().get("canManageProject"));
            assertFalse(f.projectPermissions(f.a,f.a1).capabilities().get("canReviewWork"));
        });
    }

    @Test void projectAdminIsLimitedToTheAssignedProjectAndCannotSubmitThere() {
        run(f -> {
            f.role(f.a,f.a1,"PROJECT_ADMIN"); f.role(f.a,f.a1,"USER");
            var permissions = f.permissions(f.a);
            assertFalse(permissions.capabilities().get("canCreateProjects"));
            assertTrue(permissions.companyRoles().isEmpty());
            assertEquals(1,permissions.projects().size());
            var project = f.projectPermissions(f.a,f.a1);
            assertTrue(project.capabilities().get("canManageProject"));
            assertFalse(project.capabilities().get("canSubmitWork"));
            assertTrue(project.capabilities().get("timeFallbackRequiresReason"));
            assertFalse(access.mayReview(f.a1,f.user,f.user,false,"valid reason"));
        });
    }

    @Test void projectManagerCanSubmitAndIsEligibleToReviewButCannotSelfApprove() {
        run(f -> {
            f.role(f.a,f.a1,"PROJECT_MANAGER");
            var project = f.projectPermissions(f.a,f.a1);
            assertTrue(project.capabilities().get("canSubmitWork"));
            assertTrue(project.capabilities().get("canReviewTime"));
            assertFalse(project.capabilities().get("canManageProject"));
            assertFalse(access.mayReview(f.a1,f.user,f.user,false,null));
        });
    }

    @Test void moderatorGrantIsLimitedByWorkTypeProjectCompanyAndDates() {
        run(f -> {
            f.role(f.a,null,"MODERATOR"); f.role(f.b,null,"MODERATOR");
            f.role(f.a,f.a1,"USER");
            db.update("INSERT INTO project_assignments(project_id,user_id,is_active,start_date,end_date) VALUES (?,?,TRUE,CURRENT_DATE-1,CURRENT_DATE+1)",f.a1,f.user);
            f.grant(f.a,f.a1,false,true,"2000-01-01","2100-01-01");
            f.grant(f.a,f.a2,true,false,"2000-01-01","2001-01-01");
            f.grant(f.b,f.b1,true,false,"2099-01-01","2100-01-01");
            assertEquals(1,f.permissions(f.a).projects().size());
            assertTrue(f.projectPermissions(f.a,f.a1).capabilities().get("canReviewExpenses"));
            assertFalse(f.projectPermissions(f.a,f.a1).capabilities().get("canReviewTime"));
            assertTrue(f.permissions(f.b).projects().isEmpty());
            db.update("UPDATE moderator_grants SET revoked_at=now() WHERE moderator_user_id=?",f.user);
            assertFalse(f.permissions(f.a).capabilities().get("canReviewWork"));
        });
    }

    @Test void removedRolesAndMembershipsStopGrantingPermissionsImmediately() {
        run(f -> {
            f.role(f.a,null,"COMPANY_ADMIN"); f.role(f.a,f.a1,"PROJECT_MANAGER");
            db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND role_key='COMPANY_ADMIN'",f.user);
            assertFalse(f.permissions(f.a).capabilities().get("canManageCompanyPeople"));
            db.update("UPDATE project_memberships SET status='REMOVED' WHERE user_id=?",f.user);
            assertFalse(f.permissions(f.a).capabilities().get("canReviewWork"));
            db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",f.a,f.user);
            assertThrows(AccessDeniedException.class,()->f.permissions(f.a));
        });
    }

    @Test void inactiveAccountsCannotReceivePlatformOrCompanyCapabilities() {
        run(f -> {
            f.role(f.a,null,"COMPANY_ADMIN");
            db.update("UPDATE users SET is_active=false WHERE id=?",f.user);
            assertThrows(AccessDeniedException.class,()->f.permissions(f.a));
            assertTrue(access.platformPermissions(f.user).roles().isEmpty());
        });
    }

    @Test void platformRoleNeverImplicitlyGrantsCompanyOrConfidentialAccess() {
        run(f -> {
            f.role(f.a,null,"COMPANY_ADMIN"); f.role(f.a,f.a1,"PROJECT_ADMIN");
            db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",f.user);
            assertTrue(access.platformPermissions(f.user).capabilities().get("canCreateCompanies"));
            assertFalse(access.platformPermissions(f.user).capabilities().get("canAccessCompanySupport"));
            var company = f.permissions(f.a);
            assertTrue(company.companyRoles().isEmpty());
            assertTrue(company.projects().isEmpty());
            assertTrue(company.capabilities().values().stream().noneMatch(Boolean.TRUE::equals));
            assertThrows(AccessDeniedException.class,()->access.requireCompanyCapability(f.a,f.user,"canViewCompanyReports"));
        });
    }

    @Test void companyAdminHandlesReportsWithoutReceivingOtherRestrictedGrants() {
        run(f -> {
            f.role(f.a,null,"COMPANY_ADMIN");
            var capabilities = f.permissions(f.a).capabilities();
            assertTrue(capabilities.get("canHandleConfidentialReports"));
            assertFalse(capabilities.get("canManagePerformanceReviews"));
            assertFalse(capabilities.get("canViewRestrictedPersonalFields"));
        });
    }
}
