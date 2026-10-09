package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanySettingsLocalDbTest {
    final DriverManagerDataSource datasource=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(datasource);
    final CompanyAccessService access=new CompanyAccessService(db);
    final AuditService audit=mock(AuditService.class);
    final CompanySettingsService settings=new CompanySettingsService(db,access,audit);
    final PlatformSettingsService platform=new PlatformSettingsService(db,access,audit);
    class Fixture {
        long admin,member,superadmin,a,b;
        Fixture(){
            admin=user();member=user();superadmin=user();
            String slug="settings-"+UUID.randomUUID();
            a=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Settings A',?) RETURNING id",Long.class,slug+"-a");
            b=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Settings B',?) RETURNING id",Long.class,slug+"-b");
            db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE'),(?,?,'ACTIVE'),(?,?,'ACTIVE')",a,admin,a,member,b,admin);
            db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",admin,a);
            db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",superadmin);
        }
        long user(){String key="settings-"+UUID.randomUUID();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,entra_id) VALUES (?,?,'Settings','Test','test-hash',?) RETURNING id",Long.class,key,key+"@example.com",UUID.randomUUID().toString());}
    }
    void run(Consumer<Fixture> test){new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status->{status.setRollbackOnly();test.accept(new Fixture());});}
    @Test void companyEditsNeverChangeOtherCompanyGlobalDefaultsOrAllowances(){run(f->{
        var b=db.queryForMap("SELECT * FROM company_settings WHERE company_id=?",f.b);
        var global=db.queryForList("SELECT * FROM system_settings ORDER BY id");
        var allowances=db.queryForList("SELECT * FROM leave_allowances ORDER BY id");
        var updated=settings.update(f.a,f.admin,"vacation_days_per_year",new CompanySettingsService.Input("20.25",0L));
        assertEquals("20.25",updated.values().get("vacation_days_per_year"));assertEquals(1,updated.version());
        assertEquals(b,db.queryForMap("SELECT * FROM company_settings WHERE company_id=?",f.b));
        assertEquals(global,db.queryForList("SELECT * FROM system_settings ORDER BY id"));assertEquals(allowances,db.queryForList("SELECT * FROM leave_allowances ORDER BY id"));
        verify(audit).logRequiredAction(eq(f.admin),eq(com.maxwell.chronos.enums.AuditAction.SETTINGS_UPDATED),eq("Company"),eq(f.a),contains("vacation_days_per_year"));
    });}
    @Test void activeMembershipWithoutCompanyAdminCannotReadOrEdit(){run(f->{
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.member));
        assertThrows(AccessDeniedException.class,()->settings.update(f.a,f.member,"company_name",new CompanySettingsService.Input("Forged",0L)));
        assertThrows(AccessDeniedException.class,()->settings.get(f.b,f.admin));
    });}
    @Test void globalLegacyAdminDoesNotGrantCompanyOrPlatformConfiguration(){run(f->{
        db.update("UPDATE users SET role='ADMIN' WHERE id=?",f.member);
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.member));assertThrows(AccessDeniedException.class,()->platform.get(f.member));
    });}
    @Test void removedRolesMembershipsAndInactiveAccountsImmediatelyRevokeAccess(){run(f->{
        db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND company_id=?",f.admin,f.a);
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.admin));
        db.update("UPDATE role_assignments SET removed_at=null WHERE user_id=? AND company_id=?",f.admin,f.a);
        db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",f.a,f.admin);
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.admin));
        db.update("UPDATE company_memberships SET status='ACTIVE' WHERE company_id=? AND user_id=?",f.a,f.admin);
        db.update("UPDATE users SET is_active=false WHERE id=?",f.admin);
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.admin));
    });}
    @Test void platformHasNoCompanyBypassEvenWithDualRoles(){run(f->{
        db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",f.admin);
        assertThrows(AccessDeniedException.class,()->settings.get(f.a,f.admin));
        assertThrows(AccessDeniedException.class,()->settings.update(f.a,f.admin,"timesheet.reminders.enabled",new CompanySettingsService.Input("false",0L)));
    });}
    @Test void staleEditsRejectLostUpdatesAcrossDifferentSettingKeys(){run(f->{
        settings.update(f.a,f.admin,"company_name",new CompanySettingsService.Input("A Limited",0L));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->settings.update(f.a,f.admin,"sick_days_per_year",new CompanySettingsService.Input("8",0L))).getStatusCode().value());
        assertEquals("5",settings.get(f.a,f.admin).values().get("sick_days_per_year"));assertEquals("A Limited",access.companyDisplayName(f.a));
        assertEquals("Settings A",db.queryForObject("SELECT name FROM companies WHERE id=?",String.class,f.a));
    });}
    @Test void unknownAndMalformedSettingsAreRejectedWithoutChanges(){run(f->{
        var before=settings.get(f.a,f.admin);
        for(String value:new String[]{"-1","367","NaN","1.001","1e2","","15 days"})assertThrows(IllegalArgumentException.class,()->settings.update(f.a,f.admin,"sick_days_per_year",new CompanySettingsService.Input(value,0L)));
        assertThrows(IllegalArgumentException.class,()->settings.update(f.a,f.admin,"smtp.password",new CompanySettingsService.Input("secret",0L)));
        assertThrows(IllegalArgumentException.class,()->settings.update(f.a,f.admin,"timesheet.reminders.enabled",new CompanySettingsService.Input("yes",0L)));
        assertThrows(IllegalArgumentException.class,()->settings.update(f.a,f.admin,"company_name",new CompanySettingsService.Input(" ",0L)));
        assertEquals(before,settings.get(f.a,f.admin));
    });}
    @Test void platformMasterIsSeparateValidatedAndRevisionProtected(){run(f->{
        var before=db.queryForList("SELECT * FROM company_settings ORDER BY company_id");
        var current=platform.get(f.superadmin);
        assertEquals(java.util.Set.of(PlatformSettingsService.REMINDERS),current.values().keySet());
        platform.update(f.superadmin,PlatformSettingsService.REMINDERS,new CompanySettingsService.Input("false",current.version()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->platform.update(f.superadmin,PlatformSettingsService.REMINDERS,new CompanySettingsService.Input("true",current.version()))).getStatusCode().value());
        assertThrows(IllegalArgumentException.class,()->platform.update(f.superadmin,"smtp.password",new CompanySettingsService.Input("secret",current.version()+1)));
        assertThrows(AccessDeniedException.class,()->platform.get(f.admin));
        assertEquals(before,db.queryForList("SELECT * FROM company_settings ORDER BY company_id"));
    });}
    @Test void newCompaniesHaveIndependentDefaultsAfterGlobalChanges(){run(f->{
        db.update("UPDATE system_settings SET setting_value='99' WHERE setting_key='vacation_days_per_year'");
        long company=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Fresh Company',?) RETURNING id",Long.class,"settings-fresh-"+UUID.randomUUID());
        assertEquals(15,db.queryForObject("SELECT vacation_days::integer FROM company_settings WHERE company_id=?",Integer.class,company));
        assertEquals("Fresh Company",access.companyDisplayName(company));
    });}
    @Test void remindersUseOwningCompanyAndRequireCurrentProjectSubmissionAccess(){run(f->{
        for(long company:new long[]{f.a,f.b}){
            db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE') ON CONFLICT DO NOTHING",company,f.member);
            long project=db.queryForObject("INSERT INTO projects(company_id,code,name,status) VALUES (?,'SETTINGS','Reminder test','ACTIVE') RETURNING id",Long.class,company);
            db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE')",project,f.member);
            db.update("INSERT INTO project_assignments(project_id,user_id,is_active,start_date) VALUES (?,?,true,'2026-01-01')",project,f.member);
            db.update("INSERT INTO role_assignments(user_id,company_id,project_id,role_key) VALUES (?,?,?,'USER')",f.member,company,project);
        }
        String sql=TimesheetReminderService.ELIGIBLE_ASSIGNMENTS+" AND a.user_id=?";
        assertEquals(2,db.queryForList(sql,f.member).size());
        settings.update(f.a,f.admin,"timesheet.reminders.enabled",new CompanySettingsService.Input("false",0L));
        assertEquals(1,db.queryForList(sql,f.member).size());
        db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",f.b,f.member);
        assertTrue(db.queryForList(sql,f.member).isEmpty());
        db.update("UPDATE company_memberships SET status='ACTIVE' WHERE company_id=? AND user_id=?",f.b,f.member);
        db.update("INSERT INTO role_assignments(user_id,company_id,project_id,role_key) SELECT ?,company_id,id,'PROJECT_ADMIN' FROM projects WHERE company_id=?",f.member,f.b);
        assertTrue(db.queryForList(sql,f.member).isEmpty());
        db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND role_key='PROJECT_ADMIN'",f.member);
        db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",f.member);
        assertTrue(db.queryForList(sql,f.member).isEmpty());
    });}
    @Test void migrationBackfillsKnownDefaultsWithoutCopyingGlobalCompanyName(){
        new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status->{
            status.setRollbackOnly();String schema="settings_test_"+UUID.randomUUID().toString().replace("-","");
            db.execute("CREATE SCHEMA "+schema);db.execute("SET LOCAL search_path TO "+schema);
            db.execute("CREATE TABLE companies(id bigint PRIMARY KEY,name varchar(150))");
            db.execute("CREATE TABLE system_settings(setting_key varchar(100) UNIQUE,setting_value text)");
            db.execute("INSERT INTO companies VALUES (1,'Own A'),(2,'Own B')");
            db.execute("INSERT INTO system_settings VALUES ('company_name','Global Name'),('vacation_days_per_year','20.25'),('sick_days_per_year','invalid'),('timesheet.reminders.enabled','false')");
            try(var input=new org.springframework.core.io.ClassPathResource("db/migration/V53__company_settings.sql").getInputStream()){
                db.execute(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
            assertEquals("Own A",db.queryForObject("SELECT company_name FROM company_settings WHERE company_id=1",String.class));
            assertEquals("Own B",db.queryForObject("SELECT company_name FROM company_settings WHERE company_id=2",String.class));
            assertEquals(2,db.queryForObject("SELECT count(*) FROM company_settings WHERE vacation_days=20.25 AND sick_days=5 AND reminders_enabled=false",Integer.class));
            db.execute("INSERT INTO companies VALUES (3,'New Company')");
            assertTrue(db.queryForObject("SELECT vacation_days=15 AND reminders_enabled=true FROM company_settings WHERE company_id=3",Boolean.class));
        });
    }
}
