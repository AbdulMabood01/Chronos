package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.time.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyCommunicationsLocalDbTest {
    final DriverManagerDataSource source=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/chronos_dev","chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(source);
    final CompanyAccessService access=new CompanyAccessService(db);
    final UserRepository users=mock(UserRepository.class);
    final CompanyWorkflowAccess flow=new CompanyWorkflowAccess(db,access,users);
    final EmailAlertService email=mock(EmailAlertService.class);
    final CompanyAnnouncementService announcements=new CompanyAnnouncementService(db,flow);
    final CompanyFeedbackReviewService reviews=new CompanyFeedbackReviewService(db,flow,email);
    final CompanyEmployeeReportService reports=new CompanyEmployeeReportService(db,flow,email);
    class Fixture {
        long a,b,admin,otherAdmin,member,handler,platform;Map<Long,User> actors=new HashMap<>();
        Fixture(){a=company();b=company();admin=user();otherAdmin=user();member=user();handler=user();platform=user();for(long company:new long[]{a,b})for(long u:new long[]{admin,otherAdmin,member,handler,platform})db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE')",company,u);role(a,admin);role(a,otherAdmin);db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",platform);}
        long company(){return db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Communications test',?) RETURNING id",Long.class,"comms-"+UUID.randomUUID());}
        long user(){String key="comms-"+UUID.randomUUID();long id=db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,entra_id) VALUES (?,?,'Communication','Test','hash',?) RETURNING id",Long.class,key,key+"@example.com",UUID.randomUUID().toString());User u=User.builder().id(id).email(key+"@example.com").firstName("Communication").lastName("Test").isActive(true).build();actors.put(id,u);when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));return id;}
        String email(long user){return actors.get(user).getEmail();}
        void role(long company,long u){db.update("INSERT INTO role_assignments(company_id,user_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",company,u);}
        UUID grant(long u,CompanyWorkflowAccess.Permission p,Long subject){return flow.grant(a,email(admin),new CompanyWorkflowAccess.Grant(u,p,subject,LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),"Assigned duty"));}
        CompanyAnnouncementService.Input news(String content){return new CompanyAnnouncementService.Input("Company news",content,LocalDate.now().minusDays(1),null,CompanyAnnouncementService.Priority.NORMAL,CompanyAnnouncementService.Status.PUBLISHED,true,0,"notice.txt",Base64.getEncoder().encodeToString("file".getBytes()),false);}
        CompanyFeedbackReviewService.Review review(long employee,int version){return new CompanyFeedbackReviewService.Review(employee,2026,4,"Private evaluation",null,null,null,null,null,version);}
        CompanyEmployeeReportService.Submission report(boolean anonymous,List<Long> involved){return new CompanyEmployeeReportService.Submission(CompanyEmployeeReportService.Category.OTHER_INCIDENT,"Private case","Sensitive text",null,null,null,null,anonymous,true,involved);}
    }
    void run(Consumer<Fixture> test){new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status->{status.setRollbackOnly();test.accept(new Fixture());});}
    @Test void announcementsTrackingAttachmentsAndVersionsStayWithinCompany(){run(f->{
        UUID id=announcements.save(f.a,f.email(f.admin),null,f.news("A only"));assertEquals(1,announcements.list(f.a,f.email(f.member),false).size());assertTrue(announcements.list(f.b,f.email(f.member),false).isEmpty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->announcements.open(f.b,f.email(f.member),id,false)).getStatusCode().value());
        announcements.open(f.a,f.email(f.member),id,false);announcements.acknowledge(f.a,f.email(f.member),id,0);assertEquals(1L,announcements.tracking(f.a,f.email(f.admin),id).get("acknowledged"));
        assertArrayEquals("file".getBytes(),(byte[])announcements.attachment(f.a,f.email(f.member),id,false).get("attachment_data"));
        announcements.status(f.a,f.email(f.admin),id,CompanyAnnouncementService.Status.ARCHIVED,0);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->announcements.status(f.a,f.email(f.admin),id,CompanyAnnouncementService.Status.PUBLISHED,0)).getStatusCode().value());assertTrue(announcements.list(f.a,f.email(f.member),false).isEmpty());
        assertThrows(AccessDeniedException.class,()->announcements.save(f.b,f.email(f.admin),null,f.news("Forbidden")));assertThrows(AccessDeniedException.class,()->announcements.list(f.a,f.email(f.platform),true));
    });}
    @Test void privateFeedbackIsOnlySentAndReceivedNotCompanyAdministration(){run(f->{
        reviews.submit(f.a,f.email(f.member),new CompanyFeedbackReviewService.Feedback(f.handler,"Private feedback",null,true));
        var received=reviews.feedback(f.a,f.email(f.handler),false);assertEquals(1,received.size());assertNull(received.getFirst().get("sender_name"));assertFalse(received.getFirst().containsKey("sender_id"));
        assertEquals(1,reviews.feedback(f.a,f.email(f.member),true).size());assertTrue(reviews.feedback(f.a,f.email(f.admin),false).isEmpty());assertTrue(reviews.feedback(f.b,f.email(f.handler),false).isEmpty());
        assertThrows(IllegalArgumentException.class,()->reviews.submit(f.a,f.email(f.member),new CompanyFeedbackReviewService.Feedback(f.member,"Self",null,false)));
        long outsider=f.user();assertThrows(AccessDeniedException.class,()->reviews.submit(f.a,f.email(f.member),new CompanyFeedbackReviewService.Feedback(outsider,"Cross company",null,false)));
        assertTrue(reviews.search(f.a,f.email(f.member),"Communication").stream().noneMatch(r->((Number)r.get("id")).longValue()==outsider));
    });}
    @Test void reviewGrantsAreExplicitSubjectBoundedAndPublicationCannotBeSelfApproved(){run(f->{
        assertThrows(AccessDeniedException.class,()->reviews.save(f.a,f.email(f.admin),null,f.review(f.member,0)));
        UUID grant=f.grant(f.handler,CompanyWorkflowAccess.Permission.PERFORMANCE_REVIEW,f.member);UUID id=reviews.save(f.a,f.email(f.handler),null,f.review(f.member,0));
        assertTrue(reviews.reviews(f.a,f.email(f.member),null).isEmpty());assertTrue(reviews.reviews(f.a,f.email(f.admin),null).isEmpty());assertTrue(reviews.reviews(f.b,f.email(f.handler),null).isEmpty());
        assertThrows(AccessDeniedException.class,()->reviews.save(f.a,f.email(f.handler),null,f.review(f.otherAdmin,0)));assertThrows(AccessDeniedException.class,()->reviews.publish(f.a,f.email(f.member),id,0));
        reviews.save(f.a,f.email(f.handler),id,f.review(f.member,0));assertEquals(409,assertThrows(ResponseStatusException.class,()->reviews.publish(f.a,f.email(f.handler),id,0)).getStatusCode().value());reviews.publish(f.a,f.email(f.handler),id,1);
        assertEquals(1,reviews.reviews(f.a,f.email(f.member),null).size());assertEquals(3,reviews.history(f.a,f.email(f.handler),id).size());
        reviews.save(f.a,f.email(f.handler),id,f.review(f.member,2));assertTrue(reviews.reviews(f.a,f.email(f.member),null).isEmpty());reviews.publish(f.a,f.email(f.handler),id,3);assertEquals(1,reviews.reviews(f.a,f.email(f.member),null).size());
        flow.revoke(f.a,f.email(f.admin),grant,0);assertThrows(AccessDeniedException.class,()->reviews.history(f.a,f.email(f.handler),id));assertFalse(access.companyPermissions(f.a,f.handler).capabilities().get("canManagePerformanceReviews"));
    });}
    @Test void grantsRejectSelfCompanyCrossoverAndExpiryAndRemainAuditedWithoutContent(){run(f->{
        assertThrows(AccessDeniedException.class,()->f.grant(f.admin,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null));assertThrows(AccessDeniedException.class,()->flow.grants(f.b,f.email(f.admin)));
        UUID id=f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);assertTrue(access.companyPermissions(f.a,f.handler).capabilities().get("canHandleConfidentialReports"));
        db.update("UPDATE company_sensitive_grants SET ends_on=CURRENT_DATE-1,starts_on=CURRENT_DATE-2 WHERE id=?",id);assertThrows(AccessDeniedException.class,()->reports.list(f.a,f.email(f.handler),null,null,null,null,0));
        var audit=flow.audit(f.a,f.email(f.admin),0);assertEquals("SENSITIVE_ACCESS_GRANTED",audit.getFirst().get("action"));assertFalse(audit.getFirst().containsKey("purpose"));assertFalse(audit.getFirst().containsKey("details"));assertTrue(flow.audit(f.b,f.email(f.admin),0).isEmpty());
    });}
    @Test void companyAdminsHandleReportsWithoutGrantsAndNotificationsFollowCurrentAccess(){run(f->{
        assertTrue(access.companyPermissions(f.a,f.admin).capabilities().get("canHandleConfidentialReports"));
        assertEquals(true,flow.configuration(f.a,f.email(f.member)).get("handlerConfigured"));
        UUID id=reports.submit(f.a,f.email(f.member),f.report(false,List.of()),List.of()).reportId();
        assertEquals(1,reports.list(f.a,f.email(f.admin),null,null,null,null,0).size());
        assertNotNull(reports.detail(f.a,f.email(f.admin),id));
        assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.member),id));
        assertThrows(AccessDeniedException.class,()->reports.detail(f.b,f.email(f.admin),id));
        assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.platform),id));
        verify(email).enqueueCompany(eq(f.a),eq(f.admin),eq(EmailAlertService.Category.REPORTS),anyString(),eq("/confidential-reports"),eq(id),eq(true));
        EmailAlertService alerts=new EmailAlertService(db);
        var item=Map.<String,Object>of("company_id",f.a,"user_id",f.admin,"category","REPORTS","resource_id",id,"handler_only",true);
        assertTrue(alerts.companyDeliveryAllowed(item));
        reports.review(f.a,f.email(f.admin),id,new CompanyEmployeeReportService.Review(CompanyEmployeeReportService.Status.UNDER_REVIEW,"Reviewed by admin",null,null,false,0L));
        db.update("UPDATE role_assignments SET removed_at=now() WHERE company_id=? AND user_id=? AND role_key='COMPANY_ADMIN'",f.a,f.admin);
        assertFalse(alerts.companyDeliveryAllowed(item));
        assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.admin),id));
    });}
    @Test void confidentialCasesAllowCompanyAdminsAndRespectSubjectAndReporterRecusal(){run(f->{
        f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);f.grant(f.otherAdmin,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);
        UUID id=reports.submit(f.a,f.email(f.member),f.report(false,List.of(f.handler)),List.of()).reportId();
        assertTrue(reports.list(f.a,f.email(f.handler),null,null,null,null,0).isEmpty());assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.handler),id));assertNotNull(reports.detail(f.a,f.email(f.admin),id));
        var detail=reports.detail(f.a,f.email(f.otherAdmin),id);assertFalse(detail.containsKey("recusal_salt"));assertFalse(detail.containsKey("reporter_id"));assertNotNull(detail.get("reporter"));
        assertThrows(AccessDeniedException.class,()->reports.detail(f.b,f.email(f.otherAdmin),id));assertEquals(1,reports.mine(f.a,f.email(f.member),0).size());assertTrue(reports.mine(f.b,f.email(f.member),0).isEmpty());
        reports.recuse(f.a,f.email(f.otherAdmin),id);assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.otherAdmin),id));
        assertTrue(flow.audit(f.a,f.email(f.admin),0).stream().noneMatch(r->id.toString().equals(r.get("entityId"))));
    });}
    @Test void anonymousAttachmentsHistoryAndNotificationsNeverExposeReporterOrReachRecusedHandlers(){run(f->{
        f.grant(f.member,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);
        UUID id=reports.submit(f.a,f.email(f.member),f.report(true,List.of()),List.of(new MockMultipartFile("attachments","reporter-name.txt","text/plain","Evidence".getBytes()))).reportId();
        assertNull(db.queryForObject("SELECT reporter_id FROM employee_reports WHERE id=?",Long.class,id));assertTrue(reports.mine(f.a,f.email(f.member),0).isEmpty());assertThrows(AccessDeniedException.class,()->reports.detail(f.a,f.email(f.member),id));
        var detail=reports.detail(f.a,f.email(f.handler),id);assertFalse(detail.containsKey("reporter"));assertFalse(detail.containsKey("recusal_salt"));var attachments=(List<Map<String,Object>>)detail.get("attachments");String filename=(String)attachments.getFirst().get("filename");assertFalse(filename.contains("reporter-name"));
        UUID file=(UUID)attachments.getFirst().get("id");assertArrayEquals("Evidence".getBytes(),reports.download(f.a,f.email(f.handler),id,file).content());
        verify(email,never()).enqueueCompany(eq(f.a),eq(f.member),eq(EmailAlertService.Category.REPORTS),anyString(),eq("/confidential-reports"),eq(id),eq(true));verify(email).enqueueCompany(eq(f.a),eq(f.handler),eq(EmailAlertService.Category.REPORTS),anyString(),eq("/confidential-reports"),eq(id),eq(true));
    });}
    @Test void caseUpdatesNeedCurrentVersionAndReporterSeesOnlyExplicitlySharedUpdates(){run(f->{
        f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);UUID id=reports.submit(f.a,f.email(f.member),f.report(false,List.of()),List.of()).reportId();
        reports.review(f.a,f.email(f.handler),id,new CompanyEmployeeReportService.Review(CompanyEmployeeReportService.Status.UNDER_REVIEW,"Internal only","Private actions",null,false,0L));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->reports.review(f.a,f.email(f.handler),id,new CompanyEmployeeReportService.Review(CompanyEmployeeReportService.Status.INVESTIGATION,null,null,null,false,0L))).getStatusCode().value());
        var own=reports.myDetail(f.a,f.email(f.member),id);assertFalse(own.toString().contains("Internal only"));assertFalse(own.toString().contains("Private actions"));
        reports.review(f.a,f.email(f.handler),id,new CompanyEmployeeReportService.Review(CompanyEmployeeReportService.Status.INVESTIGATION,"Still internal","Public progress",null,true,1L));assertTrue(reports.myDetail(f.a,f.email(f.member),id).toString().contains("Public progress"));
    });}
    @Test void removingMembershipRevokesGrantsAndReactivationCannotRestoreSensitiveAccess(){run(f->{
        f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);CompanyMembershipService memberships=new CompanyMembershipService(db,access,mock(AuditService.class));
        memberships.changeStatus(f.a,f.handler,f.admin,new CompanyMembershipService.StatusInput("REMOVED",0L));assertThrows(AccessDeniedException.class,()->reports.list(f.a,f.email(f.handler),null,null,null,null,0));
        memberships.changeStatus(f.a,f.handler,f.admin,new CompanyMembershipService.StatusInput("ACTIVE",1L));assertFalse(access.companyPermissions(f.a,f.handler).capabilities().get("canHandleConfidentialReports"));
    });}
    @Test void notificationDeliveryRechecksCompanyAndHandlerEligibility(){run(f->{
        UUID grant=f.grant(f.handler,CompanyWorkflowAccess.Permission.CONFIDENTIAL_HANDLER,null);UUID id=reports.submit(f.a,f.email(f.member),f.report(false,List.of()),List.of()).reportId();EmailAlertService alerts=new EmailAlertService(db);
        var item=new HashMap<String,Object>(Map.of("company_id",f.a,"user_id",f.handler,"category","REPORTS","resource_id",id,"handler_only",true));assertTrue(alerts.companyDeliveryAllowed(item));flow.revoke(f.a,f.email(f.admin),grant,0);assertFalse(alerts.companyDeliveryAllowed(item));
        assertFalse(alerts.companyDeliveryAllowed(Map.of("user_id",f.handler,"category","ANNOUNCEMENTS")));
    });}
    @Test void publicationNotificationsAreDroppedWhenTheReviewReturnsToDraftOrRecipientDoesNotMatch(){run(f->{
        f.grant(f.handler,CompanyWorkflowAccess.Permission.PERFORMANCE_REVIEW,f.member);UUID id=reviews.save(f.a,f.email(f.handler),null,f.review(f.member,0));EmailAlertService alerts=new EmailAlertService(db);
        var item=Map.<String,Object>of("company_id",f.a,"user_id",f.member,"category","PERFORMANCE","resource_id",id);assertFalse(alerts.companyDeliveryAllowed(item));reviews.publish(f.a,f.email(f.handler),id,0);assertTrue(alerts.companyDeliveryAllowed(item));
        assertFalse(alerts.companyDeliveryAllowed(Map.of("company_id",f.a,"user_id",f.admin,"category","PERFORMANCE","resource_id",id)));reviews.save(f.a,f.email(f.handler),id,f.review(f.member,1));assertFalse(alerts.companyDeliveryAllowed(item));
    });}
    @Test void letterPdfUsesCompanyBrandAndContainsAllLongTextWithoutLegacyEmployerOrSignature() throws Exception {
        try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(CompanyLetterPdf.render("Company A","Employment letter","LTR-1",true,"Company A\nOctober 5, 2026\n\n"+"A long paragraph of employment details. ".repeat(700)))){
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(pdf.getNumberOfPages()>1);assertTrue(text.contains("Company A"));assertFalse(text.contains("Maxwell"));assertFalse(text.contains("933751606"));assertFalse(text.contains("Syed"));assertEquals("Company A",pdf.getDocumentInformation().getAuthor());
        }
    }
}
