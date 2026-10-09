package com.maxwell.chronos.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.flywaydb.core.Flyway;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
class CompanyBillingLocalDbTest {
    static final DriverManagerDataSource source=new DriverManagerDataSource(System.getProperty("chronos.testDbUrl","jdbc:postgresql://localhost:5432/chronos_dev"),"chronos_user","chronos_password");
    final JdbcTemplate db=new JdbcTemplate(source);
    final TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final CompanyEntitlements entitlements=new CompanyEntitlements(db);
    final CompanyAccessService access=new CompanyAccessService(db);
    final StripeGateway stripe=mock(StripeGateway.class);
    final CompanyBillingService billing=new CompanyBillingService(db,access,entitlements,new PlanCatalog(),stripe,json,new DataSourceTransactionManager(source));
    long company,admin;String email;
    @BeforeAll static void migrate(){Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
    void fixture(){String key="billing-test-"+UUID.randomUUID();email=key+"@example.com";
        company=db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Billing Test',?) RETURNING id",Long.class,key);
        admin=user(email);db.update("INSERT INTO company_memberships(company_id,user_id,status,workforce_enabled) VALUES (?,?,'ACTIVE',FALSE)",company,admin);
        db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",admin,company);
    }
    long user(String email){return db.queryForObject("INSERT INTO users(employee_id,first_name,last_name,email,password_hash,is_active) VALUES (?,'Billing','Test',?,'test-hash',TRUE) RETURNING id",Long.class,UUID.randomUUID().toString(),email);}
    void run(Runnable test){tx.executeWithoutResult(status->{status.setRollbackOnly();fixture();test.run();});}
    UUID quote(String plan,int months,int extra){return (UUID)billing.quote(company,email,new CompanyBillingService.Selection(plan,months,extra,"PLAN")).get("id");}
    ObjectNode session(UUID id,String session,long amount){var s=json.createObjectNode();s.put("id",session);s.put("mode","payment");s.put("livemode",false);s.put("payment_status","paid");s.put("currency","usd");s.put("amount_subtotal",amount);s.put("amount_total",amount);s.put("client_reference_id",id.toString());s.put("payment_intent","pi_"+id.toString().replace("-",""));s.putObject("total_details").put("amount_tax",0);return s;}
    UUID pay(UUID id){when(stripe.checkoutEnabled()).thenReturn(true);var purchase=billing.purchaseStatus(company,email,id);String session="cs_test_"+id.toString().replace("-","");var response=json.createObjectNode().put("id",session).put("url","https://checkout.stripe.com/test");when(stripe.checkout(anyMap(),anyString())).thenReturn(response);billing.checkout(company,email,id);
        event(session(id,session,((Number)purchase.get("amount_cents")).longValue()),"checkout.session.completed","evt_"+id.toString().replace("-",""));
        assertEquals("FULFILLED",billing.purchaseStatus(company,email,id).get("status"));return id;
    }
    void event(ObjectNode object,String type,String id){var e=json.createObjectNode().put("id",id).put("type",type).put("created",Instant.now().getEpochSecond()).put("livemode",false);e.putObject("data").set("object",object);byte[] bytes=e.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);when(stripe.verify(bytes,"signed")).thenReturn(e);billing.webhook(bytes,"signed");}
    @Test void newFreeCompanyCountsAdminsAndDeduplicatesInvitations(){run(()->{
        assertEquals(1,entitlements.state(company).activeUsers());
        for(int i=0;i<6;i++){String person="reserve-"+i+"-"+UUID.randomUUID()+"@example.com";entitlements.requirePerson(company,person,true);db.update("INSERT INTO company_invitations(company_id,invitee_email,role_key,token_hash,created_by_user_id,expires_at) VALUES (?,?,'PROJECT_ADMIN',?,?,now()+interval '1 day')",company,person,UUID.randomUUID().toString().replace("-","").repeat(2),admin);}
        assertEquals(6,entitlements.state(company).reservations());assertThrows(ResponseStatusException.class,()->entitlements.requirePerson(company,"eighth@example.com",false));
        String reserved=db.queryForObject("SELECT invitee_email FROM company_invitations WHERE company_id=? LIMIT 1",String.class,company);assertDoesNotThrow(()->entitlements.requirePerson(company,reserved.toUpperCase(Locale.ROOT),true));
    });}
    @Test void trialIsOnceOnlyPaidAdminsAreFreeAndExpiryPreservesRecords(){run(()->{
        billing.trial(company,email);assertEquals("TRIAL",entitlements.state(company).source());assertEquals(0,entitlements.state(company).activeUsers());assertEquals(175,entitlements.state(company).capacity());assertThrows(ResponseStatusException.class,()->billing.trial(company,email));
        UUID term=entitlements.state(company).termId();db.update("UPDATE company_billing_terms SET starts_at=now()-interval '40 days',ends_at=now()-interval '1 day' WHERE id=?",term);
        assertTrue(entitlements.state(company).grace());assertEquals("FREE",entitlements.state(company).plan());
        for(int i=0;i<8;i++)db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE')",company,user(UUID.randomUUID()+"@example.com"));
        db.update("UPDATE company_billing_terms SET ends_at=now()-interval '8 days' WHERE id=?",term);assertTrue(entitlements.state(company).restricted());assertThrows(ResponseStatusException.class,()->entitlements.requireWork(company,admin));assertEquals(9,db.queryForObject("SELECT count(*) FROM company_memberships WHERE company_id=?",Integer.class,company));
    });}
    @Test void confirmedPaymentsAreIdempotentAndReceiptsContainFinancialDetails(){run(()->{
        UUID id=pay(quote("PRO",12,0));var s=entitlements.state(company);assertEquals(75,s.capacity());assertEquals(3,s.projects());
        var p=db.queryForMap("SELECT * FROM company_billing_purchases WHERE id=?",id);ObjectNode session=session(id,(String)p.get("stripe_session"),143040);
        event(session,"checkout.session.completed","evt_duplicate");event(session,"checkout.session.async_payment_succeeded","evt_reordered");
        assertEquals(1,db.queryForObject("SELECT count(*) FROM company_billing_terms WHERE company_id=? AND source='PAID'",Integer.class,company));assertEquals(1,db.queryForObject("SELECT count(*) FROM company_billing_receipts WHERE company_id=?",Integer.class,company));
        UUID receipt=db.queryForObject("SELECT id FROM company_billing_receipts WHERE company_id=?",UUID.class,company);try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(billing.receipt(company,email,receipt))){String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("1430.40"));assertTrue(text.contains("Stripe payment"));assertTrue(text.contains("not a tax invoice"));}catch(Exception ex){throw new RuntimeException(ex);}
        billing.reissue(company,email,receipt);assertEquals(p.get("paid_at"),db.queryForMap("SELECT paid_at FROM company_billing_purchases WHERE id=?",id).get("paid_at"));
    });}
    @Test void redirectsAndUnpaidSessionsDoNotGrantAccessAndMismatchesAreRecoverable(){run(()->{
        UUID id=quote("PRO",3,0);assertEquals("FREE",entitlements.state(company).plan());String sessionId="cs_test_unpaid";db.update("UPDATE company_billing_purchases SET stripe_session=?,status='CHECKOUT' WHERE id=?",sessionId,id);
        var s=session(id,sessionId,44700);s.put("payment_status","unpaid");event(s,"checkout.session.completed","evt_unpaid");assertEquals("FREE",entitlements.state(company).plan());
        s.put("payment_status","paid");s.put("amount_subtotal",1);event(s,"checkout.session.async_payment_succeeded","evt_badamount");assertEquals("FREE",entitlements.state(company).plan());assertNull(db.queryForObject("SELECT processed_at FROM company_billing_events WHERE event_id='evt_badamount'",java.sql.Timestamp.class));
    });}
    @Test void stalePaidQuoteIsRecordedWithoutOverwritingEntitlements(){run(()->{
        UUID id=quote("PRO",3,0);db.update("UPDATE company_billing_purchases SET stripe_session='cs_test_stale',status='CHECKOUT' WHERE id=?",id);billing.trial(company,email);
        event(session(id,"cs_test_stale",44700),"checkout.session.completed","evt_stale");assertEquals("RECONCILIATION",billing.purchaseStatus(company,email,id).get("status"));assertEquals("TRIAL",entitlements.state(company).source());assertNotNull(db.queryForMap("SELECT paid_at FROM company_billing_purchases WHERE id=?",id).get("paid_at"));
    });}
    @Test void seatsKeepTermEndAndUpgradeCarriesDurationWhileRenewalIsFuture(){run(()->{
        pay(quote("PRO",3,0));var original=entitlements.state(company);UUID seat=(UUID)billing.quote(company,email,new CompanyBillingService.Selection("PRO",3,5,"SEATS")).get("id");pay(seat);assertEquals(80,entitlements.state(company).capacity());assertEquals(original.endsAt(),entitlements.state(company).endsAt());
        Instant before=Instant.now();UUID upgrade=quote("PRO_PLUS",6,0);var q=billing.purchaseStatus(company,email,upgrade);assertTrue(CompanyEntitlements.instant(q.get("ends_at")).isAfter(new PlanCatalog().end(before,6).plus(Duration.ofDays(80))));pay(upgrade);assertEquals(175,entitlements.state(company).capacity());assertEquals(0,entitlements.state(company).extraSeats());
        var upgraded=entitlements.state(company);UUID renewal=quote("PRO_PLUS",3,0);assertEquals(upgraded.endsAt(),CompanyEntitlements.instant(billing.purchaseStatus(company,email,renewal).get("starts_at")));pay(renewal);assertEquals(upgraded.termId(),entitlements.state(company).termId());assertThrows(ResponseStatusException.class,()->quote("PRO_MAX",3,0));
    });}
    @Test void billingCannotBeAccessedByProjectRolesOtherCompaniesOrRemovedAdmins(){run(()->{
        long member=user(UUID.randomUUID()+"@example.com");db.update("INSERT INTO company_memberships(company_id,user_id,status) VALUES (?,?,'ACTIVE')",company,member);db.update("INSERT INTO role_assignments(user_id,company_id,role_key) VALUES (?,?,'PROJECT_ADMIN')",member,company);
        String other=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,member);assertThrows(AccessDeniedException.class,()->billing.summary(company,other));assertThrows(AccessDeniedException.class,()->billing.summary(company+100000,email));
        db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id=?",company,admin);assertThrows(AccessDeniedException.class,()->billing.summary(company,email));
    });}
    @Test void refundRequiresProviderConfirmationAndCannotResetEligibility(){run(()->{
        UUID id=pay(quote("PRO",3,0));String intent=(String)db.queryForMap("SELECT payment_intent FROM company_billing_purchases WHERE id=?",id).get("payment_intent");
        var refund=json.createObjectNode().put("id","re_test_refund").put("payment_intent",intent).put("amount",44700).put("status","pending");when(stripe.refund(intent,id)).thenReturn(refund);
        billing.refund(company,email,id,new CompanyBillingService.Refund("Requested cancellation"));assertEquals("PAID",entitlements.state(company).source());assertEquals("REFUND_PENDING",billing.purchaseStatus(company,email,id).get("status"));
        refund.put("status","succeeded");event(refund,"refund.updated","evt_refund");assertEquals("REFUNDED",billing.purchaseStatus(company,email,id).get("status"));assertEquals("FREE",entitlements.state(company).plan());assertEquals(1,db.queryForObject("SELECT count(*) FROM company_billing_receipts WHERE company_id=?",Integer.class,company));
    });}
    @Test void selfApprovalStaysDeniedForEveryTierAndSuspensionSurvivesPayment(){run(()->{
        for(var p:new PlanCatalog().plans())assertFalse(access.mayReview(999,admin,admin,false,"Reason"));
        UUID id=quote("PRO",3,0);db.update("UPDATE company_billing_purchases SET stripe_session='cs_test_suspended',status='CHECKOUT' WHERE id=?",id);db.update("UPDATE companies SET is_suspended=TRUE WHERE id=?",company);
        event(session(id,"cs_test_suspended",44700),"checkout.session.completed","evt_suspended");assertEquals("PAID",entitlements.state(company).source());assertEquals(Boolean.TRUE,db.queryForMap("SELECT is_suspended FROM companies WHERE id=?",company).get("is_suspended"));assertDoesNotThrow(()->billing.summary(company,email));
    });}
    @Test void eachPaidPoolCountsAnEmployeeOnceAndPreventsAdminRoleBypasses(){run(()->{
        for(var plan:new PlanCatalog().plans().stream().filter(p->p.monthlyCents()>0).toList()){
            db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=?",company);
            db.update("UPDATE company_memberships SET status='REMOVED' WHERE company_id=? AND user_id<>?",company,admin);
            db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version) VALUES (?,?,?,'CONTRACT',now(),now()+interval '1 day',?,?,?)",UUID.randomUUID(),company,plan.key(),plan.projects(),plan.users(),PlanCatalog.VERSION);
            String key="capacity-"+UUID.randomUUID();
            db.update("WITH inserted AS (INSERT INTO users(employee_id,first_name,last_name,email,password_hash) SELECT md5(?||g::text),'Capacity','Test',?||g||'@example.com','test-hash' FROM generate_series(1,?) g RETURNING id) INSERT INTO company_memberships(company_id,user_id,status,workforce_enabled) SELECT ?,id,'ACTIVE',TRUE FROM inserted",key,key,plan.users(),company);
            assertEquals(plan.users(),entitlements.state(company).activeUsers());
            String existing=db.queryForObject("SELECT email FROM users u JOIN company_memberships m ON m.user_id=u.id WHERE m.company_id=? AND m.status='ACTIVE' AND m.workforce_enabled LIMIT 1",String.class,company);
            assertDoesNotThrow(()->entitlements.requirePerson(company,existing,true));
            assertDoesNotThrow(()->entitlements.requirePerson(company,"pure-admin@example.com",false));
            assertThrows(ResponseStatusException.class,()->entitlements.requirePerson(company,"extra-employee@example.com",true));
            assertThrows(ResponseStatusException.class,()->entitlements.enroll(company,admin));
        }
    });}
    @Test void completedProjectsReleaseSlotsAndReopeningRechecksTheSameAllowance(){run(()->{
        db.update("INSERT INTO projects(company_id,code,name,status,is_active) VALUES (?,'FINISHED','Finished','COMPLETED',false),(?,'ARCHIVE','Archive','ARCHIVED',false)",company,company);
        assertDoesNotThrow(()->entitlements.requireProjectSlot(company));
        db.update("INSERT INTO projects(company_id,code,name,status,is_active) VALUES (?,'DRAFT','Draft','DRAFT',false)",company);
        assertThrows(ResponseStatusException.class,()->entitlements.requireProjectSlot(company));
    });}
    @Test void webhookRecoversTheProviderSuccessLocalSessionWriteGap(){run(()->{
        UUID id=quote("PRO",3,0);db.update("UPDATE company_billing_purchases SET status='CHECKOUT' WHERE id=?",id);
        event(session(id,"cs_test_gap",44700),"checkout.session.completed","evt_gap");
        assertEquals("FULFILLED",billing.purchaseStatus(company,email,id).get("status"));assertEquals("cs_test_gap",db.queryForObject("SELECT stripe_session FROM company_billing_purchases WHERE id=?",String.class,id));
    });}
    @Test void refundOfSeatsReleasesOnlyThatExplicitCapacityAndProtectsDependentBasePurchase(){run(()->{
        UUID base=pay(quote("PRO",3,0));UUID seat=(UUID)billing.quote(company,email,new CompanyBillingService.Selection("PRO",3,5,"SEATS")).get("id");pay(seat);
        assertThrows(ResponseStatusException.class,()->billing.refund(company,email,base,new CompanyBillingService.Refund("Base has a seat dependency")));
        var p=db.queryForMap("SELECT * FROM company_billing_purchases WHERE id=?",seat);var response=json.createObjectNode().put("id","re_seats").put("payment_intent",(String)p.get("payment_intent")).put("amount",((Number)p.get("amount_cents")).longValue()).put("status","succeeded");when(stripe.refund((String)p.get("payment_intent"),seat)).thenReturn(response);
        billing.refund(company,email,seat,new CompanyBillingService.Refund("Unused added capacity"));assertEquals(75,entitlements.state(company).capacity());assertEquals("PAID",entitlements.state(company).source());
    });}
    @Test void receiptReissueDoesNotExtendTheSevenDayRefundWindow(){run(()->{
        UUID id=pay(quote("PRO",3,0));db.update("UPDATE company_billing_purchases SET paid_at=now()-interval '8 days' WHERE id=?",id);UUID receipt=db.queryForObject("SELECT id FROM company_billing_receipts WHERE purchase_id=?",UUID.class,id);billing.reissue(company,email,receipt);
        assertThrows(ResponseStatusException.class,()->billing.refund(company,email,id,new CompanyBillingService.Refund("Outside eligibility")));verify(stripe,never()).refund(anyString(),any());
    });}
    @Test void cancellationWaitsForStripeAndPartialExternalRefundsRequireReview(){run(()->{
        UUID id=quote("PRO",3,0);db.update("UPDATE company_billing_purchases SET stripe_session='cs_test_cancel',status='CHECKOUT' WHERE id=?",id);when(stripe.expire("cs_test_cancel")).thenReturn(json.createObjectNode().put("status","expired").put("payment_status","unpaid"));billing.cancelCheckout(company,email,id);assertEquals("CANCELED",billing.purchaseStatus(company,email,id).get("status"));assertEquals("FREE",entitlements.state(company).plan());
        UUID paid=pay(quote("PRO",3,0));String intent=db.queryForObject("SELECT payment_intent FROM company_billing_purchases WHERE id=?",String.class,paid);
        event(json.createObjectNode().put("id","re_partial").put("amount",100).put("payment_intent",intent).put("status","succeeded"),"refund.updated","evt_partial");assertEquals("RECONCILIATION",billing.purchaseStatus(company,email,paid).get("status"));assertEquals("PAID",entitlements.state(company).source());
    });}
    @Test void platformRecoveryShowsOnlyBillingMetadataAndCannotRetryAnotherCompanyEvent(){run(()->{
        UUID purchase=pay(quote("PRO",3,0));long operator=user(UUID.randomUUID()+"@example.com");String address=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,operator);db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",operator);
        var repository=mock(com.maxwell.chronos.repository.UserRepository.class);when(repository.findByEmail(address)).thenReturn(Optional.of(com.maxwell.chronos.domain.User.builder().id(operator).email(address).build()));
        var platform=new PlatformAdministrationService(db,access,repository,mock(OnboardingService.class),mock(AuthSessionService.class));
        var metadata=platform.billingMetadata(company,address);assertEquals(Set.of("purchases","events"),metadata.keySet());
        @SuppressWarnings("unchecked") var purchases=(List<Map<String,Object>>)metadata.get("purchases");assertEquals(1,purchases.size());assertFalse(purchases.getFirst().containsKey("billing_snapshot"));assertFalse(purchases.getFirst().containsKey("billing_email"));
        String event="evt_"+purchase.toString().replace("-","");assertDoesNotThrow(()->platform.authorizeBillingRetry(company,address,event,"Retry confirmed provider event"));assertThrows(AccessDeniedException.class,()->platform.authorizeBillingRetry(company+10000,address,event,"Wrong scope"));
    });}
    @Test void pendingSeatRefundReservesCapacityAndSerializesConflictingPurchases(){run(()->{
        pay(quote("PRO",3,0));UUID seats=(UUID)billing.quote(company,email,new CompanyBillingService.Selection("PRO",3,1,"SEATS")).get("id");pay(seats);String key="refund-race-"+UUID.randomUUID();
        db.update("WITH inserted AS (INSERT INTO users(employee_id,first_name,last_name,email,password_hash) SELECT md5(?||g::text),'Refund','Race',?||g||'@example.com','test-hash' FROM generate_series(1,75) g RETURNING id) INSERT INTO company_memberships(company_id,user_id,status,workforce_enabled) SELECT ?,id,'ACTIVE',TRUE FROM inserted",key,key,company);
        var p=db.queryForMap("SELECT * FROM company_billing_purchases WHERE id=?",seats);var refund=json.createObjectNode().put("id","re_pending_seat").put("payment_intent",(String)p.get("payment_intent")).put("amount",num(p,"amount_cents")).put("status","pending");when(stripe.refund((String)p.get("payment_intent"),seats)).thenReturn(refund);
        billing.refund(company,email,seats,new CompanyBillingService.Refund("Unused one seat"));assertEquals(1,entitlements.state(company).refundPendingSeats());assertEquals(0,entitlements.state(company).available());
        assertThrows(ResponseStatusException.class,()->entitlements.requirePerson(company,"new-employee@example.com",true));assertThrows(ResponseStatusException.class,()->quote("PRO_PLUS",3,0));
        refund.put("status","succeeded");event(refund,"refund.updated","evt_pending_seat_refund");assertEquals(75,entitlements.state(company).capacity());assertFalse(entitlements.state(company).restricted());
    });}
    private long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}

    @Test void everyPublishedTierEnforcesExactOpenProjectBoundaryAndArchiveRelease(){run(()->{
        for(var plan:new PlanCatalog().plans()){
            db.update("UPDATE projects SET status='ARCHIVED',is_active=FALSE WHERE company_id=?",company);
            db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=?",company);
            if(!plan.key().equals("FREE"))db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version) VALUES (?,?,?,'CONTRACT',now(),now()+interval '1 day',?,?,?)",UUID.randomUUID(),company,plan.key(),plan.projects(),plan.users(),PlanCatalog.VERSION);
            for(int i=0;i<plan.projects();i++){
                assertDoesNotThrow(()->entitlements.requireProjectSlot(company));
                db.update("INSERT INTO projects(company_id,code,name,status,is_active) VALUES (?,?,'Capacity boundary','DRAFT',FALSE)",company,plan.key()+i);
            }
            assertEquals(plan.projects(),entitlements.state(company).openProjects());
            assertThrows(ResponseStatusException.class,()->entitlements.requireProjectSlot(company));
            db.update("UPDATE projects SET status='COMPLETED' WHERE company_id=? AND code=?",company,plan.key()+0);
            assertDoesNotThrow(()->entitlements.requireProjectSlot(company));
            assertEquals(plan.projects()-1,entitlements.state(company).openProjects());
        }
    });}
    @Test void termExpiryAndGraceAreExclusiveAtExactUtcInstants(){run(()->{
        Instant start=Instant.parse("2030-01-01T00:00:00Z"),end=start.plus(Duration.ofDays(30));
        db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version) VALUES (?,?,'PRO','PAID',?,?,3,75,?)",UUID.randomUUID(),company,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),PlanCatalog.VERSION);
        db.update("INSERT INTO projects(company_id,code,name,status,is_active) VALUES (?,'ONE','First','DRAFT',FALSE),(?,'TWO','Second','DRAFT',FALSE)",company,company);
        var before=new CompanyEntitlements(db,Clock.fixed(end.minusNanos(1000),ZoneOffset.UTC));
        assertEquals("PRO",before.state(company).plan());assertFalse(before.state(company).grace());
        var expiry=new CompanyEntitlements(db,Clock.fixed(end,ZoneOffset.UTC));
        assertEquals("FREE",expiry.state(company).plan());assertTrue(expiry.state(company).grace());assertFalse(expiry.state(company).restricted());
        var lastGrace=new CompanyEntitlements(db,Clock.fixed(end.plus(Duration.ofDays(7)).minusNanos(1000),ZoneOffset.UTC));
        assertTrue(lastGrace.state(company).grace());
        var after=new CompanyEntitlements(db,Clock.fixed(end.plus(Duration.ofDays(7)),ZoneOffset.UTC));
        assertFalse(after.state(company).grace());assertTrue(after.state(company).restricted());
        assertEquals(2,after.state(company).openProjects());
    });}
}

