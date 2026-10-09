package com.maxwell.chronos.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyOperationsLocalDbTest {
    final DriverManagerDataSource source=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(source);
    final CompanyAccessService access=new CompanyAccessService(db);
    final AuditService audit=mock(AuditService.class);
    final CompanyLeaveService leave=new CompanyLeaveService(db,access,audit,mock(NotificationService.class));
    class Fixture{
        long admin,member,manager,platform,a,b,pa,pb;
        Fixture(){admin=user();member=user();manager=user();platform=user();a=company();b=company();pa=project(a);pb=project(b);
            for(long company:new long[]{a,b})for(long user:new long[]{admin,member,manager})db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE')",company,user);
            role(a,admin,"COMPANY_ADMIN",null);role(a,manager,"PROJECT_MANAGER",pa);role(a,member,"USER",pa);role(b,member,"USER",pb);
            db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",platform);
            for(long project:new long[]{pa,pb}){db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE')",project,member);db.update("INSERT INTO project_assignments(project_id,user_id,is_active,start_date,end_date,planned_hours,bill_rate) VALUES (?,?,true,'2026-01-01','2026-12-31',100,25)",project,member);}
            db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE')",pa,manager);
        }
        long user(){String key="ops-"+UUID.randomUUID();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,entra_id) VALUES (?,?,'Operations','Test','test-hash',?) RETURNING id",Long.class,key,key+"@example.com",UUID.randomUUID().toString());}
        long company(){long id=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Operations Test',?) RETURNING id",Long.class,"ops-"+UUID.randomUUID());db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version,reason) VALUES (?,?,'CUSTOM','CONTRACT',now(),now()+interval '1 day',10,50,'test-fixture','Explicit capacity for operation-permission tests')",UUID.randomUUID(),id);return id;}
        long project(long company){return db.queryForObject("INSERT INTO projects(company_id,code,name,status,is_active) VALUES (?,'OPS','Operations project','ACTIVE',true) RETURNING id",Long.class,company);}
        void role(long company,long user,String role,Long project){db.update("INSERT INTO role_assignments(user_id,company_id,project_id,role_key) VALUES (?,?,?,?)",user,company,project,role);}
        CompanyLeaveService.RequestInput input(){return new CompanyLeaveService.RequestInput(LocalDate.of(2026,10,6),LocalDate.of(2026,10,6),com.maxwell.chronos.enums.VacationType.VACATION,"test",null,null);}
        long draft(long company,long user){return ((Number)leave.save(company,null,user,input()).get("id")).longValue();}
        void publish(long company,long actor){var p=leave.preview(company,actor,2026);leave.publish(company,actor,new CompanyLeaveService.PolicyInput(2026,((Number)p.get("settingsVersion")).longValue(),((Number)p.get("policyVersion")).longValue(),true));}
    }
    void run(Consumer<Fixture> test){new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status->{status.setRollbackOnly();test.accept(new Fixture());});}
    @Test void policyAndOverridesAreIndependentAndEmploymentDatesUseTheOwningMembership(){run(f->{
        db.update("UPDATE company_settings SET vacation_days=20 WHERE company_id=?",f.a);
        var beforeGlobal=db.queryForList("SELECT * FROM leave_allowances");
        f.publish(f.a,f.admin);assertEquals(new BigDecimal("20.00"),leave.balance(f.a,f.member,2026,f.member).vacation().allowanceDays());assertFalse(leave.balance(f.b,f.member,2026,f.member).configured());
        var before=leave.balance(f.a,f.member,2026,f.admin);
        leave.allowance(f.a,f.member,f.admin,new CompanyLeaveService.AllowanceInput(2026,new BigDecimal("25"),new BigDecimal("5"),new BigDecimal("3"),new BigDecimal("2"),BigDecimal.ZERO,"Exception",before.version()));
        db.update("UPDATE company_settings SET vacation_days=30 WHERE company_id=?",f.a);f.publish(f.a,f.admin);
        var balance=leave.balance(f.a,f.member,2026,f.admin);assertEquals(new BigDecimal("25.00"),balance.vacation().allowanceDays());assertEquals(new BigDecimal("2.00"),balance.vacation().extraDays());assertEquals("OVERRIDE",balance.source());
        assertEquals(beforeGlobal,db.queryForList("SELECT * FROM leave_allowances"));
        db.update("UPDATE company_memberships SET joining_date='2027-01-01' WHERE company_id=? AND user_id=?",f.a,f.manager);
        assertFalse(leave.balance(f.a,f.manager,2025,f.admin).configured());
    });}
    @Test void policyPreviewAndAllowanceRevisionsRejectStaleEdits(){run(f->{
        var preview=leave.preview(f.a,f.admin,2026);db.update("UPDATE company_settings SET version=version+1 WHERE company_id=?",f.a);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->leave.publish(f.a,f.admin,new CompanyLeaveService.PolicyInput(2026,((Number)preview.get("settingsVersion")).longValue(),-1L,true))).getStatusCode().value());
        f.publish(f.a,f.admin);var first=leave.balance(f.a,f.member,2026,f.admin);
        var input=new CompanyLeaveService.AllowanceInput(2026,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,"Extra",first.version());
        leave.allowance(f.a,f.member,f.admin,input);assertEquals(409,assertThrows(ResponseStatusException.class,()->leave.allowance(f.a,f.member,f.admin,input)).getStatusCode().value());
        assertEquals(new BigDecimal("1.00"),leave.balance(f.a,f.member,2026,f.admin).vacation().extraDays());
    });}
    @Test void requestsAndBalanceConsumptionStayInCompanyAndSelfApprovalIsDenied(){run(f->{
        f.publish(f.a,f.admin);long a=f.draft(f.a,f.member),b=f.draft(f.b,f.member);leave.submit(f.a,a,f.member,0);leave.submit(f.b,b,f.member,0);
        assertEquals(1,leave.mine(f.a,f.member).size());assertEquals(1,leave.mine(f.b,f.member).size());
        assertThrows(AccessDeniedException.class,()->leave.queue(f.b,f.admin));assertEquals(404,assertThrows(ResponseStatusException.class,()->leave.decide(f.a,b,f.admin,true,new CompanyLeaveService.Decision(1L,null,null))).getStatusCode().value());
        leave.decide(f.a,a,f.admin,true,new CompanyLeaveService.Decision(1L,null,null));assertEquals(BigDecimal.ONE,leave.balance(f.a,f.member,2026,f.member).vacation().usedDays());assertEquals(BigDecimal.ZERO,leave.balance(f.b,f.member,2026,f.member).vacation().usedDays());
        long own=f.draft(f.a,f.admin);leave.submit(f.a,own,f.admin,0);assertThrows(AccessDeniedException.class,()->leave.decide(f.a,own,f.admin,true,new CompanyLeaveService.Decision(1L,null,null)));
    });}
    @Test void approvalClearsOnlyOwningCompanyDraftHoursAndSessions(){run(f->{
        f.publish(f.a,f.admin);long request=f.draft(f.a,f.member);leave.submit(f.a,request,f.member,0);
        for(long company:new long[]{f.a,f.b}){long project=company==f.a?f.pa:f.pb;long ts=db.queryForObject("INSERT INTO timesheets(company_id,user_id,year,month,status,total_hours) VALUES (?,?,2026,10,'DRAFT',8) RETURNING id",Long.class,company,f.member);
            long entry=db.queryForObject("INSERT INTO time_entries(timesheet_id,project_id,entry_date,hours) VALUES (?,?,'2026-10-06',8) RETURNING id",Long.class,ts,project);
            db.update("INSERT INTO time_entry_sessions(time_entry_id,login_time,logout_time) VALUES (?,'09:00','17:00')",entry);
        }
        leave.decide(f.a,request,f.admin,true,new CompanyLeaveService.Decision(1L,null,null));
        assertEquals(BigDecimal.ZERO,db.queryForObject("SELECT sum(e.hours) FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE t.company_id=?",BigDecimal.class,f.a).stripTrailingZeros());
        assertEquals(new BigDecimal("8.00"),db.queryForObject("SELECT sum(e.hours) FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE t.company_id=?",BigDecimal.class,f.b));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM time_entry_sessions s JOIN time_entries e ON e.id=s.time_entry_id JOIN timesheets t ON t.id=e.timesheet_id WHERE t.company_id=?",Integer.class,f.b));
    });}
    @Test void submittedHoursAndInsufficientQuotaPreventApproval(){run(f->{
        f.publish(f.a,f.admin);db.update("UPDATE company_leave_allowances SET vacation_days=0 WHERE company_id=? AND user_id=?",f.a,f.member);
        long request=f.draft(f.a,f.member);leave.submit(f.a,request,f.member,0);assertThrows(IllegalArgumentException.class,()->leave.decide(f.a,request,f.admin,true,new CompanyLeaveService.Decision(1L,null,null)));
        db.update("UPDATE company_leave_allowances SET vacation_days=15 WHERE company_id=? AND user_id=?",f.a,f.member);
        long ts=db.queryForObject("INSERT INTO timesheets(company_id,user_id,year,month,status,total_hours) VALUES (?,?,2026,10,'SUBMITTED',8) RETURNING id",Long.class,f.a,f.member);db.update("INSERT INTO time_entries(timesheet_id,project_id,entry_date,hours) VALUES (?,?,'2026-10-06',8)",ts,f.pa);
        assertThrows(IllegalArgumentException.class,()->leave.decide(f.a,request,f.admin,true,new CompanyLeaveService.Decision(1L,null,null)));assertEquals("SUBMITTED",leave.mine(f.a,f.member).get(0).get("status"));
    });}
    @Test void overlapRevisionRemovalAndPlatformRestrictionsApply(){run(f->{
        long first=f.draft(f.a,f.member),second=f.draft(f.a,f.member);leave.submit(f.a,first,f.member,0);assertThrows(IllegalArgumentException.class,()->leave.submit(f.a,second,f.member,0));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->leave.submit(f.a,first,f.member,0)).getStatusCode().value());
        db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",f.a,f.member);assertThrows(AccessDeniedException.class,()->leave.mine(f.a,f.member));assertEquals(0,leave.mine(f.b,f.member).size());
        assertThrows(AccessDeniedException.class,()->leave.preview(f.a,f.platform,2026));assertThrows(AccessDeniedException.class,()->leave.save(f.a,null,f.platform,f.input()));
    });}
    @Test void calendarShowsOnlyAuthorizedTeamDatesAndNoLeaveNotes(){run(f->{
        f.publish(f.a,f.admin);long id=f.draft(f.a,f.member);leave.submit(f.a,id,f.member,0);leave.decide(f.a,id,f.admin,true,new CompanyLeaveService.Decision(1L,null,null));
        var calendar=leave.calendar(f.a,f.manager,2026,10);assertEquals(1,calendar.size());assertEquals(Set.of("id","userId","userName","startDate","endDate"),calendar.get(0).keySet());
        assertThrows(AccessDeniedException.class,()->leave.calendar(f.b,f.manager,2026,10));assertThrows(AccessDeniedException.class,()->leave.calendar(f.a,f.member,2026,10));
    });}
    @Test void companyAdminCreatesOnlyInOwnCompanyAndHasNoAutomaticManagementOfExistingProjects(){run(f->{
        assertTrue(access.mayCreateProject(f.a,f.admin));assertFalse(access.mayCreateProject(f.b,f.admin));assertFalse(access.mayCreateProject(f.a,f.platform));assertFalse(access.mayManageProject(f.pa,f.admin));
        var repo=mock(com.maxwell.chronos.repository.ProjectRepository.class);
        var users=mock(com.maxwell.chronos.repository.UserRepository.class);
        var actor=com.maxwell.chronos.domain.User.builder().id(f.admin).isActive(true).role(com.maxwell.chronos.enums.UserRole.EMPLOYEE).build();
        when(users.findById(f.admin)).thenReturn(Optional.of(actor));
        when(repo.save(any())).thenAnswer(call->{com.maxwell.chronos.domain.Project project=call.getArgument(0);project.setId(db.queryForObject("INSERT INTO projects(company_id,owner_user_id,code,name,status,is_active) VALUES (?,?,?,?,'DRAFT',false) RETURNING id",Long.class,project.getCompanyId(),project.getOwnerUserId(),project.getCode(),project.getName()));return project;});
        var service=new ProjectService(repo,mock(com.maxwell.chronos.repository.ProjectAssignmentRepository.class),mock(com.maxwell.chronos.repository.ProjectHourPlanRepository.class),mock(com.maxwell.chronos.repository.TimesheetRepository.class),mock(com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository.class),users,audit,mock(NotificationService.class),db,access);
        var input=new com.maxwell.chronos.dto.SaveProjectRequest();input.setCompanyId(f.a);input.setCode("NEW");input.setName("Admin-created draft");var project=service.saveProject(null,input,actor);
        assertEquals(f.a,project.getCompanyId());assertTrue(access.mayManageProject(project.getId(),f.admin));assertFalse(access.mayManageProject(f.pa,f.admin));
        input.setCode("OTHER-OWNER");input.setOwnerUserId(f.member);var delegated=service.saveProject(null,input,actor);
        assertEquals(f.member,db.queryForObject("SELECT owner_user_id FROM projects WHERE id=?",Long.class,delegated.getId()));assertTrue(access.mayManageProject(delegated.getId(),f.member));assertFalse(access.mayManageProject(delegated.getId(),f.admin));
        input.setCompanyId(f.b);assertThrows(AccessDeniedException.class,()->service.saveProject(null,input,actor));
    });}
    @Test void migrationRetainsAmbiguousHistoryAndMapsOnlyUnambiguousOwnership(){
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status->{status.setRollbackOnly();String schema="ops_migration_"+UUID.randomUUID().toString().replace("-","");db.execute("CREATE SCHEMA "+schema);db.execute("SET LOCAL search_path TO "+schema);
            db.execute("CREATE TABLE companies(id bigint PRIMARY KEY)");db.execute("INSERT INTO companies VALUES(1),(2)");
            db.execute("CREATE TABLE company_memberships(company_id bigint,user_id bigint,status varchar(12),UNIQUE(company_id,user_id))");db.execute("INSERT INTO company_memberships VALUES(1,1,'ACTIVE'),(2,1,'ACTIVE'),(1,2,'REMOVED')");
            db.execute("CREATE TABLE vacation_requests(id bigint PRIMARY KEY,user_id bigint,start_date date,end_date date)");db.execute("INSERT INTO vacation_requests VALUES(1,1,'2026-01-01','2026-01-02'),(2,2,'2026-01-01','2026-01-02')");
            db.execute("CREATE TABLE leave_policy_years(year integer,vacation_days numeric,sick_days numeric,bereavement_days numeric)");db.execute("INSERT INTO leave_policy_years VALUES(2026,15,5,3)");
            db.execute("CREATE TABLE leave_allowances(user_id bigint,leave_year integer,vacation_days numeric,sick_days numeric,bereavement_days numeric,extra_vacation_days numeric,extra_sick_days numeric,source varchar(16))");db.execute("INSERT INTO leave_allowances VALUES(1,2026,15,5,3,2,0,'OVERRIDE'),(2,2026,20,5,3,2,0,'OVERRIDE')");
            try(var input=new org.springframework.core.io.ClassPathResource("db/migration/V54__company_leave.sql").getInputStream()){db.execute(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
            assertNull(db.queryForObject("SELECT company_id FROM vacation_requests WHERE id=1",Long.class));assertEquals(1,db.queryForObject("SELECT company_id FROM vacation_requests WHERE id=2",Long.class));
            assertEquals(1,db.queryForObject("SELECT count(*) FROM company_leave_allowances",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM leave_allowances",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM company_leave_policies",Integer.class));
        });
    }
    @Test void companyReportsPermitOversightButDoNotGrantWorkReview(){run(f->{
        for(long project:new long[]{f.pa,f.pb})db.update("INSERT INTO timesheet_approval_periods(user_id,project_id,company_id,period_start,period_end,frequency,status) VALUES (?,?,?,'2026-10-01','2026-10-31','MONTHLY','APPROVED')",f.member,project,project==f.pa?f.a:f.b);
        var report=new ReportService(mock(com.maxwell.chronos.repository.TimesheetRepository.class),mock(com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository.class),mock(com.maxwell.chronos.repository.ProjectAssignmentRepository.class),mock(com.maxwell.chronos.repository.VacationRequestRepository.class),mock(com.maxwell.chronos.repository.TimeEntryRepository.class),mock(com.maxwell.chronos.repository.UserRepository.class),mock(com.maxwell.chronos.repository.ProjectRepository.class),access,db);
        assertEquals(1,report.approvalPeriods(2026,10,f.admin).size());assertEquals(f.a,((Number)report.approvalPeriods(2026,10,f.admin).get(0).get("companyId")).longValue());assertFalse(access.mayReview(f.pa,f.admin,f.member,false,"test"));assertFalse(report.canReadProjectReport(f.pb,f.admin));assertFalse(report.canReadProjectReport(f.pa,f.platform));
    });}
}
