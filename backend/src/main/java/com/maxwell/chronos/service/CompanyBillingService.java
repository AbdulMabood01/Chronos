package com.maxwell.chronos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class CompanyBillingService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final CompanyEntitlements entitlements;
    private final PlanCatalog catalog;
    private final StripeGateway stripe;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public CompanyBillingService(JdbcTemplate db,CompanyAccessService access,CompanyEntitlements entitlements,PlanCatalog catalog,
        StripeGateway stripe,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager manager){
        this.db=db;this.access=access;this.entitlements=entitlements;this.catalog=catalog;this.stripe=stripe;this.json=json;this.tx=new TransactionTemplate(manager);
    }
    public record Selection(@NotBlank String plan,@Min(3) @Max(12) int months,@Min(0) @Max(100000) int extraSeats,@NotBlank @Pattern(regexp="PLAN|SEATS") String kind) {}
    public record Profile(@NotBlank @Size(max=200) String legalName,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=1000) String address,@Min(0) long revision) {}
    public record Reduction(@NotBlank String plan,@Min(0) @Max(100000) int extraSeats,@Min(0) long revision) {}
    public record Refund(@NotBlank @Size(max=500) String reason) {}
    public record Workforce(boolean enabled,@Min(0) long version) {}
    private long num(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
    private java.sql.Timestamp stamp(Instant instant){return java.sql.Timestamp.from(instant);}
    public long actor(long company,String email){
        var found=db.queryForList("SELECT u.id FROM users u JOIN company_memberships m ON m.user_id=u.id JOIN role_assignments r ON r.user_id=u.id AND r.company_id=m.company_id WHERE m.company_id=? AND lower(u.email)=lower(?) AND m.status='ACTIVE' AND u.is_active AND NOT u.admin_locked AND u.password_hash IS NOT NULL AND r.role_key='COMPANY_ADMIN' AND r.project_id IS NULL AND r.removed_at IS NULL AND NOT EXISTS(SELECT 1 FROM role_assignments p WHERE p.user_id=u.id AND p.role_key='PLATFORM_ADMIN' AND p.removed_at IS NULL)",Long.class,company,email);
        if(found.isEmpty())throw new AccessDeniedException("Company Admin billing permission required");return found.getFirst();
    }
    private Map<String,Object> profile(long company){db.update("INSERT INTO company_billing_profiles(company_id) VALUES (?) ON CONFLICT DO NOTHING",company);return db.queryForMap("SELECT * FROM company_billing_profiles WHERE company_id=?",company);}
    private void audit(long company,Long actor,String action,Object reference,String reason){db.update("INSERT INTO company_billing_activity(company_id,actor_id,action,reference,reason) VALUES (?,?,?,?,?)",company,actor,action,reference==null?null:reference.toString(),reason);}
    private void revision(long company){db.update("UPDATE company_billing_profiles SET revision=revision+1 WHERE company_id=?",company);var s=entitlements.state(company);db.update("UPDATE companies SET plan_tier=?,project_limit=?,team_limit=?,platform_version=platform_version+1 WHERE id=?",s.plan(),s.projects(),s.capacity(),company);}
    public Map<String,Object> catalog(){return Map.of("version",PlanCatalog.VERSION,"plans",catalog.plans(),"terms",List.of(3,6,12),"extraSeatMonthlyCents",400,"discounts",Map.of("3",0,"6",10,"12",20),"currency","USD","trialDays",30,"graceDays",7,"freeDays",60);}
    public Map<String,Object> summary(long company,String email){return tx.execute(status->{actor(company,email);var result=new LinkedHashMap<String,Object>();
        result.put("entitlement",entitlements.state(company));result.put("profile",profile(company));result.put("checkoutEnabled",stripe.checkoutEnabled());result.put("freeEndsAt",entitlements.freeEnd(company));
        result.put("terms",db.queryForList("SELECT id,plan_key,source,status,starts_at,ends_at,project_limit,included_users,extra_seats,term_months,reason FROM company_billing_terms WHERE company_id=? ORDER BY created_at DESC LIMIT 100",company));
        result.put("purchases",db.queryForList("SELECT p.id,p.kind,p.plan_key,p.term_months,p.extra_seats,p.amount_cents,p.tax_cents,p.currency,p.status,p.starts_at,p.ends_at,p.paid_at,p.created_at,p.expires_at,p.failure_code,r.id receipt_id FROM company_billing_purchases p LEFT JOIN company_billing_receipts r ON r.purchase_id=p.id WHERE p.company_id=? ORDER BY p.created_at DESC LIMIT 100",company));
        result.put("activity",db.queryForList("SELECT action,reference,reason,created_at FROM company_billing_activity WHERE company_id=? ORDER BY id DESC LIMIT 50",company));return result;});}
    public void updateProfile(long company,String email,Profile input){tx.executeWithoutResult(status->{long actor=actor(company,email);entitlements.lock(company);var p=profile(company);if(num(p,"revision")!=input.revision())throw conflict("Billing changed. Reload before saving.");db.update("UPDATE company_billing_profiles SET legal_name=?,billing_email=?,billing_address=? WHERE company_id=?",input.legalName().trim(),input.email().trim().toLowerCase(Locale.ROOT),input.address().trim(),company);revision(company);audit(company,actor,"BILLING_PROFILE_UPDATED",null,null);});}
    public Map<String,Object> quote(long company,String email,Selection input){return tx.execute(status->{
        long actor=actor(company,email);entitlements.lock(company);actor(company,email);access.requireCompanyAvailable(company);noPendingRefund(company);var profile=profile(company);var current=entitlements.term(company);Instant now=Instant.now(),start=now,end;UUID previous=current==null?null:(UUID)current.get("id");
        if(current!=null&&"COMPLIMENTARY".equals(current.get("source")))throw conflict("This company has a complimentary plan. Ask a Platform Admin to change its allowance or revoke it before purchasing.");
        var plan=catalog.plan(input.plan());int months=input.months(),extra=input.extraSeats();PlanCatalog.Price price;
        if(input.kind().equals("SEATS")){
            if(current==null||!"PAID".equals(current.get("source")))throw new IllegalArgumentException("Extra seats require an active paid plan");
            if(!input.plan().equals(current.get("plan_key"))||months!=num(current,"term_months"))throw new IllegalArgumentException("Extra seats must match the active plan and term");
            end=CompanyEntitlements.instant(current.get("ends_at"));long seat=num(current,"seat_price_cents"),amount=catalog.prorate(extra,seat,CompanyEntitlements.instant(current.get("starts_at")),end,now);
            if(amount<1)throw new IllegalArgumentException("The term is ending; renew before purchasing seats");
            price=new PlanCatalog.Price(amount,0,amount,seat,catalog.discount(months));
        }else{
            price=catalog.price(input.plan(),months,extra);
            if(current!=null&&"PAID".equals(current.get("source"))){
                Instant oldEnd=CompanyEntitlements.instant(current.get("ends_at"));
                var oldPlan=catalog.plan((String)current.get("plan_key"));
                if(plan.monthlyCents()<=oldPlan.monthlyCents())start=oldEnd;
                end=catalog.end(start,months);
                if(plan.monthlyCents()>oldPlan.monthlyCents())end=end.plus(Duration.between(now,oldEnd));
            }else end=catalog.end(start,months);
            if(db.queryForObject("SELECT count(*) FROM company_billing_terms WHERE company_id=? AND status='ACTIVE' AND starts_at>?",Integer.class,company,stamp(now))>0)throw conflict("A renewal is already scheduled");
            var used=entitlements.state(company);if(plan.projects()<used.openProjects()||plan.users()+(long)extra<used.activeUsers()+used.reservations())throw conflict("This plan must cover current projects, employees and invitations. Reduce usage or choose more capacity first.");
        }
        UUID id=UUID.randomUUID();String snapshot=encode(Map.of("name",Objects.toString(profile.get("legal_name"),db.queryForObject("SELECT name FROM companies WHERE id=?",String.class,company)),"email",Objects.toString(profile.get("billing_email"),email),"address",Objects.toString(profile.get("billing_address"),"")));
        db.update("INSERT INTO company_billing_purchases(id,company_id,actor_id,kind,plan_key,term_months,extra_seats,project_limit,included_users,amount_cents,subtotal_cents,discount_cents,seat_price_cents,catalog_version,revision,predecessor_id,starts_at,ends_at,expires_at,billing_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,company,actor,input.kind(),input.plan(),months,extra,plan.projects(),plan.users(),price.totalCents(),price.subtotalCents(),price.discountCents(),price.seatTermCents(),PlanCatalog.VERSION,num(profile,"revision"),previous,stamp(start),stamp(end),stamp(now.plus(Duration.ofHours(2))),snapshot);
        audit(company,actor,"QUOTE_CREATED",id,null);return publicQuote(db.queryForMap("SELECT * FROM company_billing_purchases WHERE id=?",id));
    });}
    private Map<String,Object> publicQuote(Map<String,Object> row){var out=new LinkedHashMap<String,Object>();for(String key:List.of("id","kind","plan_key","term_months","extra_seats","project_limit","included_users","amount_cents","subtotal_cents","discount_cents","currency","catalog_version","starts_at","ends_at","expires_at","status","checkout_url","paid_at","failure_code"))out.put(key,row.get(key));return out;}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private void noPendingRefund(long company){if(db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND status='REFUND_PENDING'",Integer.class,company)>0)throw conflict("A refund is pending. Wait for confirmation before another purchase.");}
    private Map<String,Object> purchase(long company,UUID id){return db.queryForList("SELECT * FROM company_billing_purchases WHERE company_id=? AND id=?",company,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Purchase not found"));}
    public Map<String,Object> purchaseStatus(long company,String email,UUID id){actor(company,email);return publicQuote(purchase(company,id));}
    public void cancelCheckout(long company,String email,UUID id){
        var p=tx.execute(status->{actor(company,email);entitlements.lock(company);var intent=purchase(company,id);
            if(intent.get("status").equals("QUOTED")){db.update("UPDATE company_billing_purchases SET status='CANCELED' WHERE id=?",id);return intent;}
            if(!intent.get("status").equals("CHECKOUT"))throw conflict("This purchase is already settled; use its refund policy if appropriate.");
            if(intent.get("stripe_session")==null)throw conflict("Checkout setup is still being recovered. Retry opening the same checkout before canceling it.");return intent;
        });if(p.get("status").equals("QUOTED"))return;
        JsonNode session=stripe.expire((String)p.get("stripe_session"));
        Instant paid=session.path("payment_status").asText().equals("paid")?stripe.paidAt(session.path("payment_intent").asText()):Instant.now();
        tx.executeWithoutResult(status->{entitlements.lock(company);if(session.path("payment_status").asText().equals("paid"))settle(session,paid);
            else if(session.path("status").asText().equals("expired"))db.update("UPDATE company_billing_purchases SET status='CANCELED' WHERE id=? AND status='CHECKOUT'",id);
            else throw conflict("The payment is processing. Please wait for its confirmed result.");audit(company,actor(company,email),"CHECKOUT_CANCELLATION_REVIEWED",id,null);
        });
    }
    public record CheckoutAcceptance(@NotBlank String termsVersion) {}
    public Map<String,Object> checkoutAccepted(long company,String email,UUID id,CheckoutAcceptance input){
        LegalTerms.requireCurrent(input.termsVersion());
        tx.executeWithoutResult(status->{long user=actor(company,email);entitlements.lock(company);purchase(company,id);db.update("UPDATE company_billing_purchases SET terms_version=?,terms_accepted_at=now(),terms_accepted_by=? WHERE company_id=? AND id=? AND status IN ('QUOTED','CHECKOUT')",input.termsVersion(),user,company,id);});
        return checkout(company,email,id);
    }
    public Map<String,Object> checkout(long company,String email,UUID id){
        if(!stripe.checkoutEnabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Checkout is not configured. No payment was taken.");
        var intent=tx.execute(status->{long actor=actor(company,email);entitlements.lock(company);access.requireCompanyAvailable(company);noPendingRefund(company);var p=purchase(company,id);
            if(!Set.of("QUOTED","CHECKOUT").contains(p.get("status")))throw conflict("This purchase is already settled or requires billing support");
            if(p.get("checkout_url")!=null)return p;
            if(p.get("status").equals("QUOTED")&&!CompanyEntitlements.instant(p.get("expires_at")).isAfter(Instant.now().plus(Duration.ofMinutes(31))))throw conflict("Quote expired. Request a fresh quote.");
            if(num(profile(company),"revision")!=num(p,"revision"))throw conflict("Billing changed. Request a fresh quote.");
            if(db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND id<>? AND status='CHECKOUT' AND expires_at>now()",Integer.class,company,id)>0)throw conflict("Finish or cancel the existing checkout before starting another");
            db.update("UPDATE company_billing_purchases SET status='CHECKOUT' WHERE id=?",id);audit(company,actor,"CHECKOUT_STARTED",id,null);return p;});
        if(intent.get("checkout_url")!=null)return publicQuote(intent);
        String contact;try{contact=json.readTree((String)intent.get("billing_snapshot")).path("email").asText();}catch(Exception ex){throw new IllegalStateException("Invalid billing snapshot",ex);}
        JsonNode response=stripe.checkout(intent,contact);
        return tx.execute(status->{entitlements.lock(company);db.update("UPDATE company_billing_purchases SET stripe_session=?,checkout_url=? WHERE id=? AND stripe_session IS NULL",response.path("id").asText(),response.path("url").asText(),id);return publicQuote(purchase(company,id));});
    }
    public void trial(long company,String email){tx.executeWithoutResult(status->{long actor=actor(company,email);entitlements.lock(company);access.requireCompanyAvailable(company);var p=profile(company);if(Boolean.TRUE.equals(p.get("trial_used")))throw conflict("This company has already used its trial");var current=entitlements.term(company);if(current!=null&&Set.of("PAID","CONTRACT","COMPLIMENTARY","TRIAL").contains(current.get("source")))throw conflict("A paid plan or trial is already active");
        var used=entitlements.state(company);if(used.openProjects()>7||used.activeUsers()+used.reservations()>175)throw conflict("Trial capacity cannot cover this company's current usage");
        Instant now=Instant.now(),freeEnd=entitlements.freeEnd(company);if(!freeEnd.isAfter(now))throw conflict("Free access has expired. Choose a paid plan.");Instant trialEnd=now.plus(Duration.ofDays(30));if(trialEnd.isAfter(freeEnd))trialEnd=freeEnd;UUID id=UUID.randomUUID();db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=? AND status='ACTIVE'",company);
        db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version,created_by) VALUES (?,?,'PRO_PLUS','TRIAL',?,?,7,175,?,?)",id,company,stamp(now),stamp(trialEnd),PlanCatalog.VERSION,actor);
        db.update("UPDATE company_billing_profiles SET trial_used=TRUE WHERE company_id=?",company);revision(company);audit(company,actor,"TRIAL_STARTED",id,null);});}
    public void reduce(long company,String email,Reduction input){tx.executeWithoutResult(status->{long actor=actor(company,email);entitlements.lock(company);var p=profile(company);if(num(p,"revision")!=input.revision())throw conflict("Billing changed. Reload before scheduling.");catalog.plan(input.plan());if(input.plan().equals("FREE")&&input.extraSeats()!=0)throw new IllegalArgumentException("Free has no extra-seat add-on");var current=entitlements.term(company);if(current==null||!"PAID".equals(current.get("source")))throw conflict("An active paid term is required");db.update("UPDATE company_billing_profiles SET next_plan=?,next_extra_seats=? WHERE company_id=?",input.plan(),input.extraSeats(),company);revision(company);audit(company,actor,"RENEWAL_PREFERENCE_UPDATED",null,"Next plan: "+input.plan()+", extra seats: "+input.extraSeats()+". Payment remains manual.");});}
    public void workforce(long company,long user,String email,Workforce input){tx.executeWithoutResult(status->{long actor=actor(company,email);entitlements.lock(company);access.requireCompanyAvailable(company);var members=db.queryForList("SELECT * FROM company_memberships WHERE company_id=? AND user_id=? AND status='ACTIVE' FOR UPDATE",company,user);if(members.isEmpty())throw new IllegalArgumentException("Choose an active company member");var m=members.getFirst();if(num(m,"membership_version")!=input.version())throw conflict("Membership changed; reload before changing employee access");if(input.enabled())entitlements.enroll(company,user);else{
        if(db.queryForObject("SELECT count(*) FROM role_assignments WHERE company_id=? AND user_id=? AND removed_at IS NULL AND role_key IN ('USER','PROJECT_MANAGER','MODERATOR')",Integer.class,company,user)>0)throw conflict("Remove employee project roles before disabling employee access");
        db.update("UPDATE company_memberships SET workforce_enabled=FALSE WHERE company_id=? AND user_id=?",company,user);
    }db.update("UPDATE company_memberships SET membership_version=membership_version+1 WHERE company_id=? AND user_id=?",company,user);audit(company,actor,"EMPLOYEE_ACCESS_UPDATED",user,input.enabled()?"Enabled":"Disabled");});}
    private String encode(Object object){try{return json.writeValueAsString(object);}catch(Exception ex){throw new IllegalStateException("Cannot snapshot billing document",ex);}}

    public void webhook(byte[] raw,String signature){var event=stripe.verify(raw,signature);tx.executeWithoutResult(status->db.update("INSERT INTO company_billing_events(event_id,event_type,payload) VALUES (?,?,?) ON CONFLICT DO NOTHING",event.path("id").asText(),event.path("type").asText(),new String(raw,java.nio.charset.StandardCharsets.UTF_8)));processEvent(event.path("id").asText());}
    public void processEvent(String id){
        try{tx.executeWithoutResult(status->{var rows=db.queryForList("SELECT * FROM company_billing_events WHERE event_id=? AND processed_at IS NULL FOR UPDATE",id);if(rows.isEmpty())return;var row=rows.getFirst();JsonNode event;try{event=json.readTree((String)row.get("payload"));}catch(Exception ex){throw new IllegalArgumentException("Invalid event payload");}
            JsonNode object=event.path("data").path("object");String type=(String)row.get("event_type");
            if(type.equals("checkout.session.completed")||type.equals("checkout.session.async_payment_succeeded"))settle(object,Instant.ofEpochSecond(event.path("created").asLong()));
            else if(type.equals("refund.updated")||type.equals("refund.created"))settleRefund(object);
            else if(type.equals("charge.dispute.created"))dispute(object);
            else if(type.equals("checkout.session.expired"))db.update("UPDATE company_billing_purchases SET status='CANCELED' WHERE stripe_session=? AND status='CHECKOUT'",object.path("id").asText());
            db.update("UPDATE company_billing_events SET processed_at=now(),attempts=attempts+1,failure_code=NULL WHERE event_id=?",id);
        });}catch(RuntimeException ex){tx.executeWithoutResult(status->db.update("UPDATE company_billing_events SET attempts=attempts+1,failure_code='PROCESSING_FAILED' WHERE event_id=?",id));}
    }
    private void settle(JsonNode session,Instant paidAt){
        if(!session.path("payment_status").asText().equals("paid"))return;
        var rows=db.queryForList("SELECT * FROM company_billing_purchases WHERE stripe_session=?",session.path("id").asText());
        if(rows.isEmpty()){
            // Recover a Stripe-success/local-write gap, using the stored quote and trusted account event.
            UUID reference;try{reference=UUID.fromString(session.path("client_reference_id").asText());}catch(Exception ex){throw new IllegalStateException("Session has no stored purchase");}
            rows=db.queryForList("SELECT * FROM company_billing_purchases WHERE id=? AND status='CHECKOUT' AND stripe_session IS NULL",reference);
        }
        if(rows.isEmpty())throw new IllegalStateException("Session has no bound purchase yet");
        var p=rows.getFirst();long company=num(p,"company_id");entitlements.lock(company);p=purchase(company,(UUID)p.get("id"));
        if(p.get("fulfilled_at")!=null)return;
        if(!session.has("livemode")||session.path("livemode").asBoolean()!=stripe.live()||!session.path("mode").asText().equals("payment")||!session.path("currency").asText().equals("usd")||session.path("amount_subtotal").asLong()!=num(p,"amount_cents")||!session.path("client_reference_id").asText().equals(p.get("id").toString())||session.path("payment_intent").asText().isBlank())throw new IllegalArgumentException("Payment does not match the stored quote");
        long tax=session.path("total_details").path("amount_tax").asLong();if(tax<0||session.path("amount_total").asLong()!=num(p,"amount_cents")+tax)throw new IllegalArgumentException("Payment total mismatch");
        db.update("UPDATE company_billing_purchases SET stripe_session=COALESCE(stripe_session,?) WHERE id=?",session.path("id").asText(),p.get("id"));
        db.update("UPDATE company_billing_purchases SET paid_at=COALESCE(paid_at,?),payment_intent=?,tax_cents=? WHERE id=?",stamp(paidAt),session.path("payment_intent").asText(),tax,p.get("id"));
        var usage=entitlements.state(company);
        boolean belowUsage=p.get("kind").equals("PLAN")&&(num(p,"project_limit")<usage.openProjects()||num(p,"included_users")+num(p,"extra_seats")<usage.activeUsers()+usage.reservations());
        if(belowUsage||num(profile(company),"revision")!=num(p,"revision")||!CompanyEntitlements.instant(p.get("ends_at")).isAfter(Instant.now())){db.update("UPDATE company_billing_purchases SET status='RECONCILIATION',failure_code='STALE_PAID_QUOTE' WHERE id=?",p.get("id"));audit(company,null,"PAID_PURCHASE_NEEDS_REVIEW",p.get("id"),null);return;}
        UUID termId;
        if(p.get("kind").equals("SEATS")){
            termId=(UUID)p.get("predecessor_id");var current=entitlements.term(company);if(current==null||!current.get("id").equals(termId))throw conflict("Seat term is no longer current");
            db.update("UPDATE company_billing_terms SET extra_seats=extra_seats+? WHERE id=?",num(p,"extra_seats"),termId);
        }else{
            termId=UUID.randomUUID();Instant start=CompanyEntitlements.instant(p.get("starts_at"));
            if(!start.isAfter(Instant.now()))db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=? AND status='ACTIVE' AND starts_at<=now()",company);
            db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,extra_seats,term_months,seat_price_cents,catalog_version,predecessor_id,created_by) VALUES (?,?,?,'PAID',?,?,?,?,?,?,?,?,?,?)",termId,company,p.get("plan_key"),p.get("starts_at"),p.get("ends_at"),p.get("project_limit"),p.get("included_users"),p.get("extra_seats"),p.get("term_months"),p.get("seat_price_cents"),p.get("catalog_version"),p.get("predecessor_id"),p.get("actor_id"));
        }
        db.update("UPDATE company_billing_purchases SET status='FULFILLED',term_id=?,fulfilled_at=now(),failure_code=NULL WHERE id=?",termId,p.get("id"));revision(company);
        p=purchase(company,(UUID)p.get("id"));UUID receipt=UUID.randomUUID();byte[] pdf=receiptPdf(p,receipt);
        db.update("INSERT INTO company_billing_receipts(id,company_id,purchase_id,pdf) VALUES (?,?,?,?) ON CONFLICT(purchase_id) DO NOTHING",receipt,company,p.get("id"),pdf);
        queue(company,"receipt-"+p.get("id"),"Chronos payment receipt","Your company purchase is confirmed. Download the receipt from Billing. Service ends "+p.get("ends_at")+". Renewal is manual.");
        audit(company,null,"PURCHASE_FULFILLED",p.get("id"),null);
    }
    private byte[] receiptPdf(Map<String,Object> p,UUID receipt){
        JsonNode details;try{details=json.readTree((String)p.get("billing_snapshot"));}catch(Exception ex){throw new IllegalStateException(ex);}
        String text="Receipt: CHR-"+receipt+"\nCompany: "+details.path("name").asText()+"\nBilling contact: "+details.path("email").asText()+"\nAddress: "+details.path("address").asText()+"\nPurchase: "+p.get("id")+"\nCatalog: "+p.get("catalog_version")+"\nPlan: "+p.get("plan_key")+" / "+p.get("kind")+"\nTerm months: "+p.get("term_months")+"\nExtra seats: "+p.get("extra_seats")+"\nSubtotal USD: "+money(num(p,"subtotal_cents"))+"\nDiscount USD: "+money(num(p,"discount_cents"))+"\nTax USD: "+money(num(p,"tax_cents"))+"\nPaid USD: "+money(num(p,"amount_cents")+num(p,"tax_cents"))+"\nPaid at: "+p.get("paid_at")+"\nService: "+p.get("starts_at")+" to "+p.get("ends_at")+"\nStripe payment: "+p.get("payment_intent")+"\nThis is a payment receipt. It is not a tax invoice. No automatic renewal.";
        return BillingReceiptPdf.render(text);
    }
    private String money(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
    public byte[] receipt(long company,String email,UUID id){actor(company,email);var rows=db.queryForList("SELECT pdf FROM company_billing_receipts WHERE company_id=? AND id=?",company,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt not found");return (byte[])rows.getFirst().get("pdf");}
    public void reissue(long company,String email,UUID id){tx.executeWithoutResult(status->{long actor=actor(company,email);receipt(company,email,id);db.update("UPDATE company_billing_receipts SET reissue_count=reissue_count+1 WHERE company_id=? AND id=?",company,id);queue(company,"reissue-"+UUID.randomUUID(),"Chronos receipt available","Your original receipt is available in Billing. Reissue does not change the purchase date or refund deadline.");audit(company,actor,"RECEIPT_REISSUED",id,null);});}
    public Map<String,Object> refund(long company,String email,UUID id,Refund input){
        var p=tx.execute(status->{long actor=actor(company,email);entitlements.lock(company);var purchase=purchase(company,id);
            if(Set.of("REFUND_PENDING","REFUNDED").contains(purchase.get("status")))return purchase;
            if(db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND status='CHECKOUT' AND expires_at>now()",Integer.class,company)>0)throw conflict("Finish or cancel the open checkout before requesting a refund.");
            if(!Set.of("FULFILLED","RECONCILIATION","REFUND_FAILED").contains(purchase.get("status"))||purchase.get("paid_at")==null||Instant.now().isAfter(CompanyEntitlements.instant(purchase.get("paid_at")).plus(Duration.ofDays(7))))throw conflict("Refunds are available within seven days of the original verified payment");
            if(purchase.get("term_id")!=null){
                String dependency=purchase.get("kind").equals("SEATS")?"kind='PLAN' AND predecessor_id=?":"(predecessor_id=? OR term_id=?)";
                Object[] args=purchase.get("kind").equals("SEATS")?new Object[]{company,id,purchase.get("term_id")}:new Object[]{company,id,purchase.get("term_id"),purchase.get("term_id")};
                if(db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND id<>? AND status IN ('FULFILLED','REFUND_PENDING') AND "+dependency,Integer.class,args)>0)throw conflict("A later purchase depends on this term. Contact billing support for a coordinated reversal.");
                if(purchase.get("kind").equals("SEATS")){var s=entitlements.state(company);if(s.termId()!=null&&s.termId().equals(purchase.get("term_id"))&&s.activeUsers()+s.reservations()>s.capacity()-num(purchase,"extra_seats"))throw conflict("Release employee seats and invitations before refunding this capacity");}
            }
            db.update("UPDATE company_billing_purchases SET status='REFUND_PENDING',refund_reason=?,refund_requested_by=? WHERE id=?",input.reason().trim(),actor,id);revision(company);audit(company,actor,"REFUND_REQUESTED",id,input.reason().trim());return purchase;
        });
        if(p.get("status").equals("REFUNDED"))return publicQuote(p);
        if(p.get("refund_id")!=null)return publicQuote(p);
        JsonNode response=stripe.refund((String)p.get("payment_intent"),id);
        tx.executeWithoutResult(status->{entitlements.lock(company);db.update("UPDATE company_billing_purchases SET refund_id=? WHERE id=? AND refund_id IS NULL",response.path("id").asText(),id);settleRefund(response);});return purchaseStatus(company,email,id);
    }
    private void settleRefund(JsonNode refund){var rows=db.queryForList("SELECT * FROM company_billing_purchases WHERE refund_id=?",refund.path("id").asText());
        if(rows.isEmpty()){
            rows=db.queryForList("SELECT * FROM company_billing_purchases WHERE payment_intent=?",refund.path("payment_intent").asText());
            if(rows.isEmpty())throw new IllegalStateException("Unbound refund");var external=rows.getFirst();long company=num(external,"company_id");entitlements.lock(company);
            boolean dependent=external.get("term_id")!=null&&external.get("kind").equals("PLAN")&&db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND id<>? AND status IN ('FULFILLED','REFUND_PENDING') AND (predecessor_id=? OR term_id=?)",Integer.class,company,external.get("id"),external.get("term_id"),external.get("term_id"))>0;
            if(dependent||refund.path("amount").asLong()!=num(external,"amount_cents")+num(external,"tax_cents")||external.get("refund_id")!=null){
                db.update("UPDATE company_billing_purchases SET status='RECONCILIATION',failure_code='EXTERNAL_REFUND_REVIEW' WHERE id=? AND status<>'REFUNDED'",external.get("id"));audit(company,null,"EXTERNAL_REFUND_NEEDS_REVIEW",refund.path("id").asText(),"Partial or additional provider refund recorded; capacity retained pending review");return;
            }db.update("UPDATE company_billing_purchases SET refund_id=? WHERE id=?",refund.path("id").asText(),external.get("id"));
        }
        var p=rows.getFirst();long company=num(p,"company_id");entitlements.lock(company);p=purchase(company,(UUID)p.get("id"));if(p.get("status").equals("REFUNDED"))return;
        if(!refund.path("payment_intent").asText().equals(p.get("payment_intent"))||refund.path("amount").asLong()!=num(p,"amount_cents")+num(p,"tax_cents"))throw new IllegalArgumentException("Refund mismatch");
        if(refund.path("status").asText().equals("succeeded")){
            if(p.get("kind").equals("SEATS")&&p.get("term_id")!=null){var s=entitlements.state(company);if(p.get("term_id").equals(s.termId())&&s.activeUsers()+s.reservations()>s.capacity()-num(p,"extra_seats")){db.update("UPDATE company_billing_purchases SET status='RECONCILIATION',failure_code='REFUND_CAPACITY_REVIEW' WHERE id=?",p.get("id"));audit(company,null,"CONFIRMED_REFUND_CAPACITY_REVIEW",p.get("id"),"Provider refunded money; capacity retained pending coordinated review");return;}}
            if(p.get("term_id")!=null){if(p.get("kind").equals("SEATS"))db.update("UPDATE company_billing_terms SET extra_seats=GREATEST(0,extra_seats-?) WHERE id=?",num(p,"extra_seats"),p.get("term_id"));else{db.update("UPDATE company_billing_terms SET status='REFUNDED' WHERE id=?",p.get("term_id"));if(p.get("predecessor_id")!=null)db.update("UPDATE company_billing_terms SET status='ACTIVE' WHERE id=? AND status='SUPERSEDED' AND source='PAID' AND ends_at>now()",p.get("predecessor_id"));}}
            db.update("UPDATE company_billing_purchases SET status='REFUNDED',failure_code=NULL WHERE id=?",p.get("id"));revision(company);audit(company,null,"REFUND_CONFIRMED",p.get("id"),null);queue(company,"refund-"+p.get("id"),"Chronos refund confirmed","Your original payment has been refunded. Company records are retained. Review current capacity in Billing.");
        }else if(Set.of("failed","canceled").contains(refund.path("status").asText())){db.update("UPDATE company_billing_purchases SET status='REFUND_FAILED',failure_code='PROVIDER_REFUND_FAILED' WHERE id=?",p.get("id"));audit(company,null,"REFUND_FAILED",p.get("id"),null);}
        else db.update("UPDATE company_billing_purchases SET status='REFUND_PENDING' WHERE id=?",p.get("id"));
    }
    private void dispute(JsonNode dispute){var rows=db.queryForList("SELECT * FROM company_billing_purchases WHERE payment_intent=?",dispute.path("payment_intent").asText());if(rows.isEmpty())return;var p=rows.getFirst();entitlements.lock(num(p,"company_id"));db.update("UPDATE company_billing_purchases SET status='DISPUTED',failure_code='DISPUTE_NEEDS_REVIEW' WHERE id=?",p.get("id"));audit(num(p,"company_id"),null,"PAYMENT_DISPUTED",p.get("id"),"Review required; existing access preserved pending a support decision");}
    void queue(long company,String key,String subject,String message){db.update("INSERT INTO company_billing_delivery(company_id,recipient_user_id,delivery_key,subject,message) SELECT ?,r.user_id,?||'-'||r.user_id,?,? FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.company_id=? AND r.role_key='COMPANY_ADMIN' AND r.project_id IS NULL AND r.removed_at IS NULL AND m.status='ACTIVE' ON CONFLICT(delivery_key) DO NOTHING",company,key,subject,message,company);}
    public void reconcile(){
        if(!stripe.configured())return;
        for(var row:db.queryForList("SELECT id,company_id,stripe_session,status,refund_id FROM company_billing_purchases WHERE (status='CHECKOUT' AND stripe_session IS NOT NULL) OR (status='REFUND_PENDING' AND refund_id IS NOT NULL) ORDER BY created_at LIMIT 100"))try{
            if(row.get("status").equals("REFUND_PENDING")){JsonNode refund=stripe.refundStatus((String)row.get("refund_id"));tx.executeWithoutResult(status->settleRefund(refund));}
            else{JsonNode session=stripe.session((String)row.get("stripe_session"));Instant paymentDate=session.path("payment_status").asText().equals("paid")?stripe.paidAt(session.path("payment_intent").asText()):Instant.now();tx.executeWithoutResult(status->{settle(session,paymentDate);if(session.path("status").asText().equals("expired"))db.update("UPDATE company_billing_purchases SET status='CANCELED' WHERE id=? AND status='CHECKOUT'",row.get("id"));});}
        }catch(RuntimeException ex){/* Retry next pass; never charge again. */}
        for(String id:db.queryForList("SELECT event_id FROM company_billing_events WHERE processed_at IS NULL AND attempts<50 ORDER BY received_at LIMIT 100",String.class))processEvent(id);
    }
}
