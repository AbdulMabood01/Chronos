package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real transaction/period/permission checks with repository identity lookups and notifications stubbed. */
@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class TimesheetPeriodLocalDbTest {
    final DriverManagerDataSource source=new DriverManagerDataSource(
        System.getProperty("chronos.testDbUrl","jdbc:postgresql://localhost:5432/chronos_e2e"),"chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(source);
    final TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    long company,employee,manager,owner,projectId;
    Project project;
    TimesheetPeriodService service;
    void run(Runnable test){
        if(!source.getUrl().endsWith("_e2e"))throw new IllegalStateException("Use an isolated _e2e database");
        tx.executeWithoutResult(status->{status.setRollbackOnly();fixture();test.run();});
    }
    long user(String name){String key=UUID.randomUUID().toString();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,timezone,is_active) VALUES (?,? ,?,'Test','test-hash','America/Chicago',TRUE) RETURNING id",Long.class,key,key+"@example.com",name);}
    void fixture(){
        company=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Period Test',?) RETURNING id",Long.class,"period-"+UUID.randomUUID());
        employee=user("Employee");manager=user("Manager");owner=user("Owner");
        for(long id:new long[]{employee,manager,owner})db.update("INSERT INTO company_memberships(company_id,user_id,status,workforce_enabled) VALUES (?,?,'ACTIVE',TRUE)",company,id);
        projectId=db.queryForObject("INSERT INTO projects(company_id,code,name,status,is_active,approval_frequency,owner_user_id,project_manager_id,project_manager_hours_approver_id) VALUES (?,'PERIOD','Period Test','ACTIVE',TRUE,'DAILY',?,?,?) RETURNING id",Long.class,company,owner,manager,owner);
        for(long id:new long[]{employee,manager,owner}){
            db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE')",projectId,id);
            db.update("INSERT INTO role_assignments(company_id,project_id,user_id,role_key) VALUES (?,?,?,?)",company,projectId,id,id==owner?"PROJECT_ADMIN":id==manager?"PROJECT_MANAGER":"USER");
        }
        for(long id:new long[]{employee,manager})db.update("INSERT INTO project_assignments(project_id,user_id,is_active,start_date,end_date,planned_hours,bill_rate) VALUES (?,?,TRUE,?,?,0,25)",projectId,id,LocalDate.now().minusYears(3),LocalDate.now().plusYears(3));
        var users=mock(UserRepository.class);var projects=mock(ProjectRepository.class);var entries=mock(TimeEntryRepository.class);
        User employeeUser=User.builder().id(employee).firstName("Employee").lastName("Test").timezone("America/Chicago").build();
        User managerUser=User.builder().id(manager).firstName("Manager").lastName("Test").timezone("America/Chicago").build();
        User ownerUser=User.builder().id(owner).firstName("Owner").lastName("Test").timezone("America/Chicago").build();
        when(users.findById(employee)).thenReturn(Optional.of(employeeUser));when(users.findById(manager)).thenReturn(Optional.of(managerUser));when(users.findById(owner)).thenReturn(Optional.of(ownerUser));
        project=Project.builder().id(projectId).companyId(company).code("PERIOD").name("Period Test").approvalFrequency("DAILY").projectManager(managerUser).projectManagerHoursApprover(ownerUser).build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(entries.findPeriodEntries(anyLong(),anyLong(),any(),any())).thenReturn(List.of());
        service=new TimesheetPeriodService(db,projects,users,entries,new CompanyAccessService(db),mock(NotificationService.class));
    }
    long id(Map<String,Object> item){return ((Number)item.get("id")).longValue();}
    @Test void assignmentEndIsInclusiveAndMissingOrInactiveAssignmentCannotSubmit(){run(()->{
        LocalDate date=LocalDate.now().minusDays(10);
        db.update("UPDATE project_assignments SET start_date=?,end_date=? WHERE project_id=? AND user_id=?",date.minusDays(7),date,projectId,employee);
        assertThrows(IllegalArgumentException.class,()->service.submit(employee,projectId,date.minusDays(8)));
        assertEquals("SUBMITTED",service.submit(employee,projectId,date).get("status"));
        assertThrows(IllegalArgumentException.class,()->service.submit(employee,projectId,date.plusDays(1)));
        db.update("UPDATE project_assignments SET is_active=FALSE WHERE project_id=? AND user_id=?",projectId,employee);
        assertThrows(IllegalArgumentException.class,()->service.submit(employee,projectId,date.minusDays(1)));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM timesheet_approval_periods WHERE project_id=?",Integer.class,projectId));
    });}
    @Test void zeroHourAssignedWorkCanBeSubmittedAndReviewedButNotSelfReviewed(){run(()->{
        LocalDate date=LocalDate.now().minusDays(10);var submitted=service.submit(manager,projectId,date);
        assertThrows(AccessDeniedException.class,()->service.decide(manager,id(submitted),true,null,"Self fallback"));
        assertThrows(AccessDeniedException.class,()->service.decide(manager,id(submitted),false,"Self rejection",null));
        var approved=service.decide(owner,id(submitted),true,null,null);
        assertEquals("APPROVED",approved.get("status"));assertEquals(0,((java.math.BigDecimal)approved.get("totalHours")).signum());
        assertThrows(IllegalArgumentException.class,()->service.submit(manager,projectId,date));
    });}
    @Test void unassignedOwnerCannotSubmitEvenWithCompanyAdminRole(){run(()->{
        db.update("INSERT INTO role_assignments(company_id,user_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",company,owner);
        assertThrows(AccessDeniedException.class,()->service.submit(owner,projectId,LocalDate.now()));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM timesheet_approval_periods WHERE project_id=?",Integer.class,projectId));
    });}
    @Test void batchRejectsDifferentDatesBelongingToSameWeek(){run(()->{
        project.setApprovalFrequency("WEEKLY");
        LocalDate monday=LocalDate.now().minusWeeks(3).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // Make the rollback boundary explicit for direct service calls, which do not use Spring proxies.
        var nested=new TransactionTemplate(new DataSourceTransactionManager(source));
        nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        assertThrows(IllegalArgumentException.class,()->nested.execute(status->service.submitBatch(employee,projectId,List.of(monday,monday.plusDays(1)))));
        assertEquals("DRAFT",service.view(employee,employee,projectId,monday).get("status"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM timesheet_approval_period_events e JOIN timesheet_approval_periods p ON p.id=e.period_id WHERE p.project_id=?",Integer.class,projectId));
    });}
    @Test void rejectionClearsReviewerOnResubmissionAndPreservesLateAudit(){run(()->{
        LocalDate date=LocalDate.now().minusDays(40);var submitted=service.submit(employee,projectId,date);
        var rejected=service.decide(manager,id(submitted),false,"Please correct",null);
        assertEquals("Please correct",rejected.get("rejectionReason"));
        var resubmitted=service.submit(employee,projectId,date);
        assertNull(resubmitted.get("reviewComment"));assertNull(resubmitted.get("rejectedByName"));assertEquals(true,resubmitted.get("late"));
        assertEquals(List.of("SUBMITTED_LATE","REJECTED","SUBMITTED_LATE"),service.history(employee,id(submitted)).stream().map(e->e.get("event")).toList());
    });}
    @Test void currentApprovedDayCannotOpenUntilItClosesAndReasonsAreRequired(){run(()->{
        LocalDate date=LocalDate.now();var submitted=service.submit(employee,projectId,date);service.decide(manager,id(submitted),true,null,null);
        assertThrows(IllegalArgumentException.class,()->service.requestOpening(employee,projectId,date,"Correction"));
        for(String reason:new String[]{""," ","x".repeat(501)})assertThrows(IllegalArgumentException.class,()->service.requestOpening(employee,projectId,date,reason));
        assertEquals("APPROVED",service.view(employee,employee,projectId,date).get("status"));
    });}

    @Test void expiredApprovedOpeningRefreezesHoursAndCanBeRequestedAgain(){run(()->{
        LocalDate date=LocalDate.now().minusDays(10);var submitted=service.submit(employee,projectId,date);
        service.decide(manager,id(submitted),true,null,null);service.requestOpening(employee,projectId,date,"Correct historical hours");
        Instant before=Instant.now();
        var opened=service.decideOpening(owner,id(submitted),true,null);
        Instant deadline=(Instant)opened.get("correctionUntil");
        assertFalse(deadline.isBefore(before.plus(Duration.ofDays(7))));
        assertFalse(deadline.isAfter(Instant.now().plus(Duration.ofDays(7))));
        assertEquals(true,opened.get("openingActive"));assertEquals(false,opened.get("pdfExportEligible"));
        assertThrows(IllegalArgumentException.class,()->service.decideOpening(owner,id(submitted),true,null));
        db.update("UPDATE timesheet_approval_periods SET correction_until=now()-interval '1 second' WHERE id=?",id(submitted));
        var expired=service.view(employee,employee,projectId,date);
        assertEquals(false,expired.get("editable"));assertEquals(false,expired.get("openingActive"));assertEquals(true,expired.get("pdfExportEligible"));
        assertThrows(IllegalArgumentException.class,()->service.requireEditable(employee,projectId,date));
        assertEquals("PENDING",service.requestOpening(employee,projectId,date,"New correction review").get("openingStatus"));
    });}
    @Test void removedReviewerRoleIsCheckedAtDecisionTimeWithoutChangingSubmittedWork(){run(()->{
        LocalDate date=LocalDate.now().minusDays(10);var submitted=service.submit(employee,projectId,date);
        db.update("UPDATE role_assignments SET removed_at=now() WHERE project_id=? AND user_id=? AND role_key='PROJECT_MANAGER'",projectId,manager);
        assertThrows(AccessDeniedException.class,()->service.decide(manager,id(submitted),true,null,null));
        assertEquals("SUBMITTED",service.view(employee,employee,projectId,date).get("status"));
        assertEquals(1,service.history(employee,id(submitted)).size());
        assertEquals("APPROVED",service.decide(owner,id(submitted),true,null,"Manager no longer appointed").get("status"));
    });}

    @Test void everyActualPublishedEntitlementKeepsManagerSelfApprovalAndSelfRejectionDenied(){run(()->{
        int offset=10;
        for(var plan:new PlanCatalog().plans()){
            db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=?",company);
            if(!plan.key().equals("FREE"))db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version) VALUES (?,?,?,'CONTRACT',now(),now()+interval '1 day',?,?,?)",UUID.randomUUID(),company,plan.key(),plan.projects(),plan.users(),PlanCatalog.VERSION);
            assertEquals(plan.key(),new CompanyEntitlements(db).state(company).plan());
            LocalDate date=LocalDate.now().minusDays(offset++);var submitted=service.submit(manager,projectId,date);
            assertThrows(AccessDeniedException.class,()->service.decide(manager,id(submitted),true,null,"Paid self approval"));
            assertThrows(AccessDeniedException.class,()->service.decide(manager,id(submitted),false,"Self rejection",null));
            assertEquals("SUBMITTED",service.view(manager,manager,projectId,date).get("status"));
            assertEquals("APPROVED",service.decide(owner,id(submitted),true,null,null).get("status"));
        }
    });}
}
