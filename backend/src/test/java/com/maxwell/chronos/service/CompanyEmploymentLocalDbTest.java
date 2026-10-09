package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyEmploymentLocalDbTest {
    final DriverManagerDataSource datasource=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(datasource);
    final CompanyAccessService access=new CompanyAccessService(db);
    final AuditService audit=mock(AuditService.class);
    final CompanyEmploymentService service=new CompanyEmploymentService(db,access,audit);
    class Fixture {
        long admin,employee,other,a,b;
        Fixture(){
            admin=user();employee=user();other=user();
            String key="employment-"+UUID.randomUUID();
            a=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Employment A',?) RETURNING id",Long.class,key+"-a");
            b=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Employment B',?) RETURNING id",Long.class,key+"-b");
            for(long user:new long[]{admin,employee,other})db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE')",a,user);
            db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE'),(?,?,'ACTIVE')",b,employee,b,admin);
            db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",admin,a);
        }
        long user(){String key="emp-"+UUID.randomUUID();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,job_title,joining_date,password_hash,credential_version,entra_id) VALUES (?,?,'Employment','Test','Legacy title','2020-01-01','test-password-hash',9,?) RETURNING id",Long.class,key,key+"@example.com",UUID.randomUUID().toString());}
        CompanyEmploymentService.Input input(String id,String title,long version){return new CompanyEmploymentService.Input(id,title,LocalDate.of(2026,1,1),version);}
    }
    void run(Consumer<Fixture> test){new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status->{status.setRollbackOnly();test.accept(new Fixture());});}

    @Test void editInADoesNotChangeBOrGlobalIdentity(){run(f->{
        var identity=db.queryForMap("SELECT * FROM users WHERE id=?",f.employee);
        var beforeB=service.get(f.b,f.employee,f.employee);
        var updated=service.update(f.a,f.employee,f.admin,f.input("101","Engineer",0));
        assertEquals("101",updated.employeeId());assertEquals("Engineer",updated.jobTitle());assertEquals(1,updated.version());
        assertEquals(beforeB,service.get(f.b,f.employee,f.employee));assertEquals(identity,db.queryForMap("SELECT * FROM users WHERE id=?",f.employee));
        verify(audit).logRequiredAction(eq(f.admin),eq(com.maxwell.chronos.enums.AuditAction.USER_PROFILE_UPDATED),eq("CompanyMembership"),anyLong(),contains("company "+f.a));
    });}
    @Test void companyAAdminCannotEditOrInspectOtherUsersInB(){run(f->{
        assertThrows(AccessDeniedException.class,()->service.update(f.b,f.employee,f.admin,f.input("201","Changed",0)));
        assertThrows(AccessDeniedException.class,()->service.get(f.b,f.employee,f.admin));
        assertEquals("1",service.get(f.b,f.employee,f.employee).employeeId());
    });}
    @Test void selfReadDoesNotPermitSelfEditWithoutCompanyAdminRole(){run(f->{
        assertNotNull(service.get(f.a,f.employee,f.employee));
        assertThrows(AccessDeniedException.class,()->service.update(f.a,f.employee,f.employee,f.input("SELF","Changed",0)));
        assertThrows(AccessDeniedException.class,()->service.get(f.a,f.other,f.employee));
    });}
    @Test void employeeIdsAreUniquePerCompanyButCanBeReusedAcrossCompanies(){run(f->{
        service.update(f.a,f.employee,f.admin,f.input("103","Engineer A",0));
        db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",f.admin,f.b);
        service.update(f.b,f.employee,f.admin,f.input("103","Engineer B",0));
        assertEquals("Engineer A",service.get(f.a,f.employee,f.admin).jobTitle());
        assertEquals("Engineer B",service.get(f.b,f.employee,f.admin).jobTitle());
    });}
    @Test void staleEditsAreRejectedAndDoNotOverwriteEmployment(){run(f->{
        service.update(f.a,f.employee,f.admin,f.input("101","First",0));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(f.a,f.employee,f.admin,f.input("102","Stale",0))).getStatusCode().value());
        assertEquals("First",service.get(f.a,f.employee,f.admin).jobTitle());
    });}
    @Test void duplicateEmployeeIdIsRejectedByTheDatabaseConstraint(){
        new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status->{
            status.setRollbackOnly();var f=new Fixture();
            service.update(f.a,f.employee,f.admin,f.input("101","First",0));
            Object savepoint=status.createSavepoint();
            var ex=assertThrows(ResponseStatusException.class,()->service.update(f.a,f.other,f.admin,f.input("101","Duplicate",0)));
            assertEquals(409,ex.getStatusCode().value());status.rollbackToSavepoint(savepoint);status.releaseSavepoint(savepoint);
            assertEquals("3",service.get(f.a,f.other,f.admin).employeeId());
        });
    }
    @Test void removalRetainsEmploymentButRevokesFormerMemberAccess(){run(f->{
        service.update(f.a,f.employee,f.admin,f.input("101","Retained",0));
        db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",f.a,f.employee);
        assertThrows(AccessDeniedException.class,()->service.get(f.a,f.employee,f.employee));
        assertEquals("Retained",service.get(f.a,f.employee,f.admin).jobTitle());
        assertThrows(AccessDeniedException.class,()->service.update(f.a,f.employee,f.admin,f.input("102","Changed",1)));
    });}
    @Test void newMembershipsDoNotCopyFrozenGlobalEmployment(){run(f->{
        assertEquals("2",service.get(f.a,f.employee,f.employee).employeeId());
        assertNull(service.get(f.a,f.employee,f.employee).jobTitle());
        assertNull(service.get(f.a,f.employee,f.employee).joiningDate());
    });}
    @Test void companyRosterUsesEmploymentFieldsAndSerializableRoleLists(){run(f->{
        service.update(f.a,f.employee,f.admin,f.input("101","Engineer A",0));
        var companies=new CompanyManagementService(db,access,mock(com.maxwell.chronos.repository.UserRepository.class),
                mock(InvitationDeliveryService.class),audit,mock(OnboardingService.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        var members=companies.members(f.a,f.admin);
        var employee=members.stream().filter(member->((Number)member.get("user_id")).longValue()==f.employee).findFirst().orElseThrow();
        assertEquals("Engineer A",employee.get("job_title"));assertTrue(employee.get("roles") instanceof java.util.List);
        assertDoesNotThrow(()->new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(members));
    });}
    @Test void platformAdminHasNoCompanyEmploymentBypass(){run(f->{
        db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",f.admin);
        assertThrows(AccessDeniedException.class,()->service.get(f.a,f.employee,f.admin));
        assertThrows(AccessDeniedException.class,()->service.update(f.a,f.employee,f.admin,f.input("101","Changed",0)));
    });}
    @Test void migrationPreservesLegacyValuesAndNewMembershipsStartBlank(){
        new TransactionTemplate(new DataSourceTransactionManager(datasource)).executeWithoutResult(status->{
            status.setRollbackOnly();String schema="employment_test_"+UUID.randomUUID().toString().replace("-","");
            db.execute("CREATE SCHEMA "+schema);db.execute("SET LOCAL search_path TO "+schema);
            db.execute("CREATE TABLE users(id bigint PRIMARY KEY,employee_id varchar(50),job_title varchar(120),joining_date date)");
            db.execute("CREATE TABLE company_memberships(company_id bigint,user_id bigint,status varchar(12))");
            db.execute("INSERT INTO users VALUES(1,'LEGACY-001','Original title','2020-01-01')");
            db.execute("INSERT INTO company_memberships VALUES(1,1,'ACTIVE'),(2,1,'REMOVED')");
            db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/migration/V51__company_employment_details.sql"));return null;});
            assertEquals(2,db.queryForObject("SELECT count(*) FROM company_memberships WHERE employee_id='LEGACY-001' AND job_title='Original title' AND joining_date='2020-01-01'",Integer.class));
            db.execute("INSERT INTO company_memberships(company_id,user_id,status) VALUES(3,1,'ACTIVE')");
            assertTrue(db.queryForObject("SELECT employee_id IS NULL AND job_title IS NULL AND joining_date IS NULL FROM company_memberships WHERE company_id=3",Boolean.class));
        });
    }
}
