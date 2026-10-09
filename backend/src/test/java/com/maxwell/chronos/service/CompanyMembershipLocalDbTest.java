package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyMembershipLocalDbTest {
    final DriverManagerDataSource source=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(source);
    final TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    final CompanyAccessService access=new CompanyAccessService(db);
    final AuditService audit=mock(AuditService.class);
    final CompanyMembershipService service=new CompanyMembershipService(db,access,audit);
    final CompanyManagementService companies=new CompanyManagementService(db,access,mock(UserRepository.class),mock(InvitationDeliveryService.class),audit,mock(OnboardingService.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class));
    class Fixture {
        long a,b,a1,a2,member,bAdmin,platform,pa,pb;String session;
        Fixture(){
            a1=user();a2=user();member=user();bAdmin=user();platform=user();
            session=UUID.randomUUID().toString();
            db.update("INSERT INTO auth_sessions(id,user_id,created_at,last_activity_at,expires_at) VALUES (?,?,now(),now(),now()+interval '1 hour')",session,member);
            a=company("A");b=company("B");pa=project(a);pb=project(b);
            for(long id:new long[]{a1,a2,member})membership(a,id);
            for(long id:new long[]{bAdmin,member})membership(b,id);
            role(a,a1,"COMPANY_ADMIN");role(a,a2,"COMPANY_ADMIN");role(b,bAdmin,"COMPANY_ADMIN");role(a,member,"MODERATOR");
            db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",platform);
            for(long p:new long[]{pa,pb}){
                long company=p==pa?a:b;
                db.update("INSERT INTO project_memberships(project_id,user_id,status) VALUES (?,?,'ACTIVE')",p,member);
                db.update("INSERT INTO project_assignments(project_id,user_id,is_active,start_date,planned_hours,bill_rate) VALUES (?,?,TRUE,'2026-01-01',40,25)",p,member);
                db.update("INSERT INTO role_assignments(user_id,company_id,project_id,role_key) VALUES (?,?,?,'USER')",member,company,p);
                db.update("INSERT INTO timesheets(user_id,company_id,year,month,status,total_hours) VALUES (?,?,2026,10,'APPROVED',8)",member,company);
            }
            db.update("UPDATE company_memberships SET employee_id='MEMBER-A',job_title='Engineer A',joining_date='2020-01-01' WHERE company_id=? AND user_id=?",a,member);
            db.update("INSERT INTO moderator_grants(company_id,project_id,moderator_user_id,timesheets,expenses,starts_on,ends_on,granted_by_user_id) VALUES (?,?,?,TRUE,TRUE,'2000-01-01','2100-01-01',?)",a,pa,member,a1);
            db.update("INSERT INTO company_invitations(company_id,invitee_email,role_key,token_hash,created_by_user_id,expires_at) SELECT ?,email,'PROJECT_ADMIN',?,?,now()+interval '1 day' FROM users WHERE id=?",a,"a".repeat(64),a1,member);
        }
        long user(){String id="member-"+UUID.randomUUID();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,credential_version,entra_id) VALUES (?,?,'Member','Test','test-password',9,?) RETURNING id",Long.class,id,id+"@example.com",UUID.randomUUID().toString());}
        long company(String name){return db.queryForObject("INSERT INTO companies(name,slug) VALUES (?,?) RETURNING id",Long.class,"Membership "+name,"members-"+UUID.randomUUID());}
        long project(long company){return db.queryForObject("INSERT INTO projects(company_id,code,name,status) VALUES (?,'P','Membership project','ACTIVE') RETURNING id",Long.class,company);}
        void membership(long company,long user){db.update("INSERT INTO company_memberships(company_id,user_id,status,joined_at) VALUES (?,?,'ACTIVE',now())",company,user);}
        void role(long company,long user,String role){db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,?)",user,company,role);}
        Map<String,Object> membership(long company){return db.queryForMap("SELECT * FROM company_memberships WHERE company_id=? AND user_id=?",company,member);}
    }
    void run(Consumer<Fixture> test){tx.executeWithoutResult(status->{status.setRollbackOnly();test.accept(new Fixture());});}
    CompanyMembershipService.StatusInput status(String status,long version){return new CompanyMembershipService.StatusInput(status,version);}

    @Test void removalIsCompanyScopedAndPreservesIdentityEmploymentAndHistory(){run(f->{
        var identity=db.queryForMap("SELECT * FROM users WHERE id=?",f.member);var other=f.membership(f.b);var employment=f.membership(f.a);
        var session=db.queryForMap("SELECT * FROM auth_sessions WHERE id=?",f.session);
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));
        assertEquals(identity,db.queryForMap("SELECT * FROM users WHERE id=?",f.member));assertEquals(other,f.membership(f.b));
        var removed=f.membership(f.a);assertEquals("REMOVED",removed.get("status"));
        for(String field:new String[]{"employee_id","job_title","joining_date","employment_version"})assertEquals(employment.get(field),removed.get(field));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM role_assignments WHERE company_id=? AND user_id=? AND removed_at IS NULL",Integer.class,f.a,f.member));
        assertFalse(db.queryForObject("SELECT is_active FROM project_assignments WHERE project_id=? AND user_id=?",Boolean.class,f.pa,f.member));
        assertTrue(db.queryForObject("SELECT is_active FROM project_assignments WHERE project_id=? AND user_id=?",Boolean.class,f.pb,f.member));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM timesheets WHERE user_id=? AND status='APPROVED'",Integer.class,f.member));
        assertTrue(db.queryForObject("SELECT revoked_at IS NOT NULL FROM moderator_grants WHERE company_id=? AND moderator_user_id=?",Boolean.class,f.a,f.member));
        assertTrue(db.queryForObject("SELECT revoked_at IS NOT NULL FROM company_invitations WHERE company_id=?",Boolean.class,f.a));
        assertThrows(AccessDeniedException.class,()->access.companyPermissions(f.a,f.member));
        assertTrue(access.maySubmit(f.pb,f.member));
        assertEquals(session,db.queryForMap("SELECT * FROM auth_sessions WHERE id=?",f.session));
    });}
    @Test void reactivationNeverRestoresOldRolesOrProjectAccess(){run(f->{
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));service.changeStatus(f.a,f.member,f.a1,status("ACTIVE",1));
        assertEquals("ACTIVE",f.membership(f.a).get("status"));assertTrue(access.companyPermissions(f.a,f.member).companyRoles().isEmpty());
        assertTrue(access.companyPermissions(f.a,f.member).projects().isEmpty());assertFalse(access.maySubmit(f.pa,f.member));
        assertEquals("Engineer A",f.membership(f.a).get("job_title"));
    });}
    @Test void scopeAndPlatformRestrictionsApplyToAllPeopleMutations(){run(f->{
        assertThrows(AccessDeniedException.class,()->service.changeStatus(f.b,f.member,f.a1,status("REMOVED",0)));
        assertThrows(AccessDeniedException.class,()->service.assignRole(f.b,f.member,f.a1,new CompanyMembershipService.RoleInput("COMPANY_ADMIN",0L)));
        assertThrows(AccessDeniedException.class,()->service.recoveryAddress(f.b,f.member,f.a1));
        assertThrows(AccessDeniedException.class,()->service.changeStatus(f.a,f.member,f.platform,status("REMOVED",0)));
        assertThrows(AccessDeniedException.class,()->companies.members(f.a,f.platform));
        assertThrows(IllegalArgumentException.class,()->service.assignRole(f.a,f.member,f.a1,new CompanyMembershipService.RoleInput("PLATFORM_ADMIN",0L)));
    });}
    @Test void roleChangesUseMembershipVersionsAndKeepGlobalIdentityUnchanged(){run(f->{
        var identity=db.queryForMap("SELECT * FROM users WHERE id=?",f.member);
        service.assignRole(f.a,f.member,f.a1,new CompanyMembershipService.RoleInput("PROJECT_ADMIN",0L));
        assertTrue(access.hasCompanyRole(f.a,f.member,"PROJECT_ADMIN"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0))).getStatusCode().value());
        service.removeRole(f.a,f.member,f.a1,"PROJECT_ADMIN",1L);
        companies.revokeModerator(f.a,db.queryForObject("SELECT id FROM moderator_grants WHERE company_id=? AND moderator_user_id=? AND revoked_at IS NULL LIMIT 1",Long.class,f.a,f.member),f.a1);
        assertFalse(access.hasModeratorGrant(f.pa,f.member,true));assertEquals(identity,db.queryForMap("SELECT * FROM users WHERE id=?",f.member));
    });}
    @Test void unavailableOrPendingAccountsCannotBeReactivatedOrPromoted(){run(f->{
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));db.update("UPDATE users SET admin_locked=TRUE WHERE id=?",f.member);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStatus(f.a,f.member,f.a1,status("ACTIVE",1))).getStatusCode().value());
        db.update("UPDATE company_memberships SET status='PENDING' WHERE company_id=? AND user_id=?",f.a,f.member);
        assertThrows(ResponseStatusException.class,()->service.assignRole(f.a,f.member,f.a1,new CompanyMembershipService.RoleInput("COMPANY_ADMIN",1L)));
    });}
    @Test void lastAdminProtectionCountsOnlyEffectiveActiveAdmins(){run(f->{
        db.update("UPDATE users SET admin_locked=TRUE WHERE id=?",f.a2);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStatus(f.a,f.a1,f.a1,status("REMOVED",0))).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.removeRole(f.a,f.a1,f.a1,"COMPANY_ADMIN",0L)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->companies.removeRole(f.a,null,f.a1,"COMPANY_ADMIN",f.a1)).getStatusCode().value());
    });}
    @Test void accountLockDeactivationAndPlatformPromotionCannotStrandACompany(){run(f->{
        db.update("UPDATE role_assignments SET removed_at=now() WHERE company_id=? AND user_id=? AND role_key='COMPANY_ADMIN'",f.a,f.a2);
        var users=mock(UserRepository.class);var sessions=mock(AuthSessionService.class);
        var target=User.builder().id(f.a1).role(UserRole.EMPLOYEE).isActive(true).credentialVersion(9).build();
        when(users.findForUpdate(f.a1)).thenReturn(Optional.of(target));when(users.findById(f.platform)).thenReturn(Optional.of(User.builder().id(f.platform).role(UserRole.ADMIN).build()));
        var accounts=new UserService(users,audit,sessions,db);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->accounts.deactivateUser(f.a1,f.platform)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->accounts.lockAccount(f.a1,f.platform,null)).getStatusCode().value());
        assertEquals(410,assertThrows(ResponseStatusException.class,()->accounts.changeRole(f.a1,UserRole.ADMIN,f.platform)).getStatusCode().value());
        verifyNoInteractions(sessions);verify(users,never()).save(any());assertEquals(9,target.getCredentialVersion());assertTrue(target.getIsActive());
    });}
    @Test void ownershipAndApprovalDutiesMustBeTransferredBeforeRemoval(){run(f->{
        for(String column:new String[]{"owner_user_id","project_manager_id","project_manager_hours_approver_id"}){
            db.update("UPDATE projects SET "+column+"=? WHERE id=?",f.member,f.pa);
            assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0))).getStatusCode().value());
            db.update("UPDATE projects SET "+column+"=NULL WHERE id=?",f.pa);
        }
        assertEquals("ACTIVE",f.membership(f.a).get("status"));
    });}
    @Test void pendingWorkMustBeReviewedBeforeRemoval(){run(f->{
        db.update("INSERT INTO timesheet_approval_periods(user_id,project_id,company_id,period_start,period_end,frequency,status) VALUES (?,?,?,'2026-10-01','2026-10-31','MONTHLY','SUBMITTED')",f.member,f.pa,f.a);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0))).getStatusCode().value());
        assertEquals("ACTIVE",f.membership(f.a).get("status"));
    });}
    @Test void projectAssignmentCannotReactivateARemovedCompanyMembership(){run(f->{
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));
        assertThrows(AccessDeniedException.class,()->access.activateProjectRole(f.a,f.pa,f.member,"USER",f.a1));
        assertEquals("REMOVED",f.membership(f.a).get("status"));
    });}
    @Test void removedOwnersCannotReadCompanyWorkThroughDirectRecordIds(){run(f->{
        var actor=User.builder().id(f.member).email(db.queryForObject("SELECT email FROM users WHERE id=?",String.class,f.member)).role(UserRole.EMPLOYEE).isActive(true).build();
        var users=mock(UserRepository.class);when(users.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        var projects=mock(com.maxwell.chronos.repository.ProjectRepository.class);
        when(projects.findById(f.pa)).thenReturn(Optional.of(com.maxwell.chronos.domain.Project.builder().id(f.pa).companyId(f.a).build()));
        when(projects.findById(f.pb)).thenReturn(Optional.of(com.maxwell.chronos.domain.Project.builder().id(f.pb).companyId(f.b).build()));
        long ea=db.queryForObject("INSERT INTO project_expenses(project_id,employee_id,category,amount,expense_date,description,status) VALUES (?,?,'TRAVEL',10,'2026-10-01','A history','APPROVED') RETURNING id",Long.class,f.pa,f.member);
        db.update("INSERT INTO project_expenses(project_id,employee_id,category,amount,expense_date,description,status) VALUES (?,?,'TRAVEL',20,'2026-10-01','B history','APPROVED')",f.pb,f.member);
        var expenses=new ExpenseService(db,projects,mock(com.maxwell.chronos.repository.ProjectAssignmentRepository.class),users,mock(NotificationService.class),access);
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));
        assertThrows(AccessDeniedException.class,()->expenses.detail(actor.getEmail(),ea));
        var visible=expenses.mine(actor.getEmail());assertEquals(1,visible.size());assertEquals(f.pb,((Number)visible.get(0).get("project_id")).longValue());
        var sheets=mock(com.maxwell.chronos.repository.TimesheetRepository.class);
        long sheetId=db.queryForObject("SELECT id FROM timesheets WHERE company_id=? AND user_id=?",Long.class,f.a,f.member);
        when(sheets.findForUpdate(sheetId)).thenReturn(Optional.of(com.maxwell.chronos.domain.Timesheet.builder().id(sheetId).companyId(f.a).user(actor).build()));
        var timesheets=new TimesheetService(mock(TimesheetPeriodService.class),sheets,mock(com.maxwell.chronos.repository.TimeEntryRepository.class),
            mock(com.maxwell.chronos.repository.TimesheetProjectSubmissionRepository.class),users,projects,mock(com.maxwell.chronos.repository.ProjectAssignmentRepository.class),
            mock(com.maxwell.chronos.repository.ProjectHourPlanRepository.class),mock(com.maxwell.chronos.repository.VacationRequestRepository.class),audit,mock(NotificationService.class),mock(ProjectService.class),access);
        assertThrows(AccessDeniedException.class,()->timesheets.getTimesheetById(sheetId,f.member,false));
        assertDoesNotThrow(()->access.requireActiveCompanyAccess(f.b,f.member));
    });}
    @Test void aLegacyInvitationCannotReactivateRemovedMembership(){run(f->{
        service.changeStatus(f.a,f.member,f.a1,status("REMOVED",0));
        db.update("UPDATE company_invitations SET revoked_at=NULL WHERE company_id=?",f.a); // Simulate an older, unreconciled invitation.
        var repository=mock(UserRepository.class);String email=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,f.member);
        when(repository.findById(f.member)).thenReturn(Optional.of(User.builder().id(f.member).email(email).isActive(true).passwordHash("test-password").build()));
        var invitations=new CompanyManagementService(db,access,repository,mock(InvitationDeliveryService.class),audit,mock(OnboardingService.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        long invitation=db.queryForObject("SELECT id FROM company_invitations WHERE company_id=?",Long.class,f.a);
        assertThrows(AccessDeniedException.class,()->invitations.acceptById(invitation,f.member));
        assertEquals("REMOVED",f.membership(f.a).get("status"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM role_assignments WHERE company_id=? AND user_id=? AND removed_at IS NULL",Integer.class,f.a,f.member));
    });}
    @Test void searchFiltersAndProjectAdminDirectoryStayWithinTheirScope(){run(f->{
        var active=companies.members(f.a,f.a1,"ACTIVE","Engineer A");assertEquals(1,active.size());assertEquals(f.member,((Number)active.get(0).get("user_id")).longValue());
        db.update("UPDATE role_assignments SET removed_at=now() WHERE company_id=? AND user_id=?",f.a,f.a2);
        access.activateProjectRole(f.a,f.pa,f.a2,"PROJECT_ADMIN",f.a1);
        var limited=companies.members(f.a,f.a2);assertTrue(limited.stream().allMatch(row->!row.containsKey("joining_date")&&!row.containsKey("membership_version")));
        assertTrue(limited.stream().anyMatch(row->((Number)row.get("user_id")).longValue()==f.member));
        assertFalse(limited.stream().anyMatch(row->((Number)row.get("user_id")).longValue()==f.a1));
    });}
    @ParameterizedTest @ValueSource(strings={"MEMBERSHIP","ROLE"})
    void concurrentMutualRemovalCannotRemoveBothAdmins(String kind)throws Exception{
        Fixture f=tx.execute(status->new Fixture());var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            java.util.function.BiFunction<Long,Long,Callable<Boolean>> operation=(actor,target)->()->{
                start.await();try{return tx.execute(status->{db.execute("SET LOCAL lock_timeout='5s'");
                    if(kind.equals("ROLE"))service.removeRole(f.a,target,actor,"COMPANY_ADMIN",0L);else service.changeStatus(f.a,target,actor,status("REMOVED",0));return true;});
                }catch(AccessDeniedException|ResponseStatusException denied){return false;}
            };
            var first=executor.submit(operation.apply(f.a1,f.a2));var second=executor.submit(operation.apply(f.a2,f.a1));start.countDown();
            int successes=(first.get(12,TimeUnit.SECONDS)?1:0)+(second.get(12,TimeUnit.SECONDS)?1:0);assertEquals(1,successes);
            assertEquals(1,db.queryForObject("SELECT count(*) FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.company_id=? AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE'",Integer.class,f.a));
        }finally{executor.shutdownNow();cleanup(f);}
    }
    @Test void concurrentPromotionAndAccountSuspensionCannotLeaveNoActiveAdmin()throws Exception{
        Fixture f=tx.execute(status->{var fixture=new Fixture();db.update("UPDATE role_assignments SET removed_at=now() WHERE company_id=? AND user_id=? AND role_key='COMPANY_ADMIN'",fixture.a,fixture.a2);return fixture;});
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var promotion=executor.submit(()->{start.await();return tx.execute(status->{db.execute("SET LOCAL lock_timeout='5s'");service.assignRole(f.a,f.member,f.a1,new CompanyMembershipService.RoleInput("COMPANY_ADMIN",0L));return true;});});
            var suspension=executor.submit(()->{start.await();try{return tx.execute(status->{db.execute("SET LOCAL lock_timeout='5s'");access.guardAccountAdminAccessLoss(f.a1);db.update("UPDATE users SET is_active=FALSE WHERE id=?",f.a1);return true;});}catch(ResponseStatusException denied){return false;}});
            start.countDown();assertTrue(promotion.get(12,TimeUnit.SECONDS));suspension.get(12,TimeUnit.SECONDS);
            assertTrue(db.queryForObject("SELECT count(*) FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id JOIN users u ON u.id=r.user_id WHERE r.company_id=? AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE' AND u.is_active=TRUE",Integer.class,f.a)>=1);
        }finally{executor.shutdownNow();cleanup(f);}
    }
    void cleanup(Fixture f){tx.executeWithoutResult(status->{
        db.update("DELETE FROM company_invitations WHERE company_id IN(?,?)",f.a,f.b);db.update("DELETE FROM moderator_grants WHERE company_id IN(?,?)",f.a,f.b);
        db.update("DELETE FROM role_assignments WHERE company_id IN(?,?) OR user_id=?",f.a,f.b,f.platform);
        db.update("DELETE FROM project_assignments WHERE project_id IN(?,?)",f.pa,f.pb);db.update("DELETE FROM project_memberships WHERE project_id IN(?,?)",f.pa,f.pb);
        db.update("DELETE FROM timesheets WHERE company_id IN(?,?)",f.a,f.b);db.update("DELETE FROM projects WHERE company_id IN(?,?)",f.a,f.b);
        db.update("DELETE FROM company_memberships WHERE company_id IN(?,?)",f.a,f.b);db.update("DELETE FROM companies WHERE id IN(?,?)",f.a,f.b);
        db.update("DELETE FROM auth_sessions WHERE user_id IN(?,?,?,?,?)",f.a1,f.a2,f.member,f.bAdmin,f.platform);
        db.update("DELETE FROM users WHERE id IN(?,?,?,?,?)",f.a1,f.a2,f.member,f.bAdmin,f.platform);
    });}
}
