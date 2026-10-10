package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.Instant;
import java.time.Duration;

@Service @RequiredArgsConstructor @Transactional
public class PlatformAdministrationService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final UserRepository users;
    private final OnboardingService onboarding;
    private final AuthSessionService sessions;
    public record Plan(@NotBlank @Pattern(regexp="FREE|PRO|PRO_PLUS|PRO_MAX|CUSTOM") String tier,@Min(1) @Max(100000) int projectLimit,@Min(1) @Max(100000) int teamLimit,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason, @Pattern(regexp="CONTRACT|COMPLIMENTARY") String grantType, Instant endsAt) {
        public Plan(String tier,int projectLimit,int teamLimit,Long version,String reason){this(tier,projectLimit,teamLimit,version,reason,null,null);}
    }
    public record GrantRevocation(@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason) {}
    public record Status(boolean suspended,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason) {}
    public record AccountAction(@NotBlank @Pattern(regexp="LOCK|UNLOCK|DEACTIVATE|REACTIVATE|SIGN_OUT|PROMOTE|DEMOTE|INVITE|REVOKE_INVITATION") String action,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason) {}
    public record AdminInput(@NotBlank @Size(max=100) String firstName,@NotBlank @Size(max=100) String lastName,@NotBlank @Email @Size(max=255) String email) {}
    private long actor(String email){User u=users.findByEmail(email).orElseThrow(()->new AccessDeniedException("Platform Admin permission required"));if(!Boolean.TRUE.equals(access.platformPermissions(u.getId()).capabilities().get("canConfigurePlatform"))||!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND is_active AND NOT admin_locked AND password_hash IS NOT NULL)",Boolean.class,u.getId())))throw new AccessDeniedException("Platform Admin permission required");return u.getId();}
    private void record(long actor,Long company,Long target,String action,String reason){db.update("INSERT INTO platform_activity(user_id,company_id,target_user_id,action,reason) VALUES (?,?,?,?,?)",actor,company,target,action,reason);}
    public List<Map<String,Object>> companies(String email){actor(email);return db.queryForList("SELECT c.*,EXISTS(SELECT 1 FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id WHERE r.company_id=c.id AND r.project_id IS NULL AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE') onboarding_complete, (SELECT count(*) FROM projects p WHERE p.company_id=c.id AND p.status NOT IN ('COMPLETED','ARCHIVED')) project_count,(SELECT count(*) FROM company_memberships m WHERE m.company_id=c.id AND m.status='ACTIVE') member_count FROM companies c ORDER BY c.name,c.id");}
    public List<Map<String,Object>> overview(String email){
        var rows=companies(email);var entitlements=new CompanyEntitlements(db);
        for(var row:rows){long id=((Number)row.get("id")).longValue();var state=entitlements.state(id);
            row.put("usage",state);
            row.put("admin_contacts",db.queryForList("SELECT DISTINCT u.first_name,u.last_name,u.email FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id JOIN users u ON u.id=r.user_id WHERE r.company_id=? AND r.project_id IS NULL AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE' ORDER BY u.email",id));
            row.put("billing_issues",db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND status IN ('RECONCILIATION','DISPUTED','REFUND_FAILED')",Long.class,id));
            row.put("delivery_issues",db.queryForObject("SELECT count(*) FROM invitation_delivery d JOIN company_invitations i ON i.id=d.company_invitation_id WHERE i.company_id=? AND i.role_key='COMPANY_ADMIN' AND i.project_id IS NULL AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND d.status='FAILED' AND d.id=(SELECT max(n.id) FROM invitation_delivery n WHERE n.company_invitation_id=i.id)",Long.class,id));
        }return rows;
    }
    public Map<String,Object> company(long id,String email){actor(email);return companies(email).stream().filter(c->((Number)c.get("id")).longValue()==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found"));}
    private Map<String,Object> lockedCompany(long id,long version){access.lockCompanyAdministration(id);var row=db.queryForMap("SELECT * FROM companies WHERE id=?",id);if(((Number)row.get("platform_version")).longValue()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Company administration changed. Reload and try again.");return row;}
    public Map<String,Object> usage(long company,String email){actor(email);var s=new CompanyEntitlements(db).state(company);var result=new LinkedHashMap<String,Object>();result.put("project_count",s.openProjects());result.put("active_users",s.activeUsers());result.put("reservations",s.reservations());result.put("user_capacity",s.capacity());result.put("project_limit",s.projects());result.put("plan",s.plan());result.put("source",s.source());result.put("ends_at",s.endsAt());return result;}
    public Map<String,Object> billingMetadata(long company,String email){actor(email);company(company,email);return Map.of("purchases",db.queryForList("SELECT id,kind,plan_key,amount_cents,tax_cents,currency,status,paid_at,fulfilled_at,failure_code FROM company_billing_purchases WHERE company_id=? ORDER BY created_at DESC LIMIT 100",company),"events",billingEvents(company),"audit",db.queryForList("SELECT a.action,a.reason,a.created_at,u.email actor_email FROM platform_activity a LEFT JOIN users u ON u.id=a.user_id WHERE a.company_id=? ORDER BY a.id DESC LIMIT 100",company),"deliveries",db.queryForList("SELECT d.id,d.attempts,d.next_attempt_at,d.sent_at FROM company_billing_delivery d WHERE d.company_id=? AND d.sent_at IS NULL AND d.attempts>0 ORDER BY d.id DESC LIMIT 50",company));}
    private List<Map<String,Object>> billingEvents(long company){return db.queryForList("SELECT e.event_id,e.event_type,e.received_at,e.processed_at,e.attempts,e.failure_code FROM company_billing_events e WHERE EXISTS(SELECT 1 FROM company_billing_purchases p WHERE p.company_id=? AND (e.payload::jsonb->'data'->'object'->>'id' IN (p.stripe_session,p.refund_id) OR e.payload::jsonb->'data'->'object'->>'payment_intent'=p.payment_intent OR e.payload::jsonb->'data'->'object'->>'client_reference_id'=p.id::text)) ORDER BY e.received_at DESC LIMIT 100",company);}
    public void authorizeBillingRetry(long company,String email,String event,String reason){long actor=actor(email);if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("A reason is required");if(billingEvents(company).stream().noneMatch(e->event.equals(e.get("event_id"))))throw new AccessDeniedException("Event is not bound to this company");record(actor,company,null,"BILLING_EVENT_RETRY",reason);}
    private void grantChangeAllowed(long company){
        if(db.queryForObject("SELECT count(*) FROM company_billing_terms WHERE company_id=? AND source='PAID' AND status='ACTIVE' AND (ends_at IS NULL OR ends_at>now())",Integer.class,company)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Paid terms cannot be replaced with a manual grant. Use company Billing for purchases and renewals.");
        if(db.queryForObject("SELECT count(*) FROM company_billing_purchases WHERE company_id=? AND status IN ('CHECKOUT','REFUND_PENDING','RECONCILIATION','DISPUTED')",Integer.class,company)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolve open checkout, refund or payment review before changing a company grant.");
    }
    private void requireGrantCapacity(long company,String tier,int projects,int users){
        // Free counts administration-only memberships and invitations as well as employees.
        boolean all=tier.equals("FREE");
        long active=db.queryForObject("SELECT count(*) FROM company_memberships WHERE company_id=? AND status='ACTIVE' AND (? OR workforce_enabled)",Long.class,company,all);
        long reserved=db.queryForObject("SELECT count(DISTINCT lower(i.invitee_email)) FROM company_invitations i WHERE i.company_id=? AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>now() AND (? OR i.role_key IN ('USER','PROJECT_MANAGER','MODERATOR')) AND NOT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=i.company_id AND m.status='ACTIVE' AND lower(u.email)=lower(i.invitee_email) AND (? OR m.workforce_enabled))",Long.class,company,all,all);
        if(new CompanyEntitlements(db).state(company).openProjects()>projects||active+reserved>users)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"The grant must cover existing projects, company users and reserved invitations.");
    }
    private void grantRevision(long company,String tier,int projects,int users){
        db.update("INSERT INTO company_billing_profiles(company_id,revision) VALUES (?,1) ON CONFLICT(company_id) DO UPDATE SET revision=company_billing_profiles.revision+1",company);
        db.update("UPDATE companies SET plan_tier=?,project_limit=?,team_limit=?,platform_version=platform_version+1 WHERE id=?",tier,projects,users,company);
    }
    public void plan(long company,String email,Plan input){
        long actor=actor(email);lockedCompany(company,input.version());actor(email);grantChangeAllowed(company);
        String source=input.grantType()==null?"CONTRACT":input.grantType();
        if(!Set.of("CONTRACT","COMPLIMENTARY").contains(source))throw new IllegalArgumentException("Choose a supported grant type");
        if(!input.tier().equals("CUSTOM")){var p=new PlanCatalog().plan(input.tier());if(input.projectLimit()!=p.projects()||input.teamLimit()!=p.users())throw new IllegalArgumentException("Standard plan allowances come from the catalog; use Custom for a contracted exception");}
        Instant now=Instant.now(),end=source.equals("CONTRACT")?now.plus(Duration.ofDays(90)):input.endsAt();
        if(input.tier().equals("FREE")&&end==null)throw new IllegalArgumentException("Free extensions require an explicit expiry date");
        if(end!=null&&!end.isAfter(now))throw new IllegalArgumentException("Grant expiry must be in the future");
        requireGrantCapacity(company,input.tier(),input.projectLimit(),input.teamLimit());
        db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=? AND status='ACTIVE'",company);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,ends_at,project_limit,included_users,catalog_version,created_by,reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,company,input.tier(),source,java.sql.Timestamp.from(now),end==null?null:java.sql.Timestamp.from(end),input.projectLimit(),input.teamLimit(),PlanCatalog.VERSION,actor,input.reason());
        grantRevision(company,input.tier(),input.projectLimit(),input.teamLimit());
        String action=source.equals("COMPLIMENTARY")?"COMPANY_COMPLIMENTARY_PLAN_ASSIGNED":"COMPANY_BILLING_GRANT_CREATED";
        record(actor,company,null,action,input.reason());
        db.update("INSERT INTO company_billing_activity(company_id,actor_id,action,reference) VALUES (?,?,?,?)",company,actor,action,id.toString());
    }
    public void revokeGrant(long company,String email,GrantRevocation input){
        long actor=actor(email);lockedCompany(company,input.version());actor(email);grantChangeAllowed(company);
        var term=new CompanyEntitlements(db).term(company);
        if(term==null||!"COMPLIMENTARY".equals(term.get("source")))throw new ResponseStatusException(HttpStatus.CONFLICT,"No active complimentary plan to revoke.");
        requireGrantCapacity(company,"FREE",1,7);
        db.update("UPDATE company_billing_terms SET status='SUPERSEDED' WHERE company_id=? AND source='COMPLIMENTARY' AND status='ACTIVE'",company);
        grantRevision(company,"FREE",1,7);
        record(actor,company,null,"COMPANY_COMPLIMENTARY_PLAN_REVOKED",input.reason());
        db.update("INSERT INTO company_billing_activity(company_id,actor_id,action,reference) VALUES (?,?,'COMPANY_COMPLIMENTARY_PLAN_REVOKED',?)",company,actor,term.get("id").toString());
    }
    public void status(long company,String email,Status input){long actor=actor(email);lockedCompany(company,input.version());actor(email);db.update("UPDATE companies SET is_suspended=?,suspension_reason=?,platform_version=platform_version+1 WHERE id=?",input.suspended(),input.suspended()?input.reason():null,company);record(actor,company,null,input.suspended()?"COMPANY_SUSPENDED":"COMPANY_RESUMED",input.reason());}
    public List<Map<String,Object>> accounts(String email,String query){return accounts(email,query,null);}
    public List<Map<String,Object>> accounts(String email,String query,Long companyId){
        actor(email);
        if(query!=null&&query.length()>200)throw new IllegalArgumentException("Search is too long");
        if(companyId!=null){if(companyId<=0)throw new IllegalArgumentException("Choose a valid company");company(companyId,email);}
        String search=query==null?"":query.trim();
        var rows=db.queryForList("SELECT u.id,u.email,u.first_name,u.last_name,u.is_active,u.admin_locked,u.platform_access_version,CASE WHEN u.password_hash IS NULL THEN 'INVITED' ELSE 'ACTIVE' END account_status,EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.role_key='PLATFORM_ADMIN' AND r.removed_at IS NULL AND r.company_id IS NULL) platform_admin,(SELECT count(*) FROM company_memberships m WHERE m.user_id=u.id AND m.status='ACTIVE') active_memberships,(SELECT status FROM invitation_delivery d WHERE d.employee_id=u.id ORDER BY d.id DESC LIMIT 1) delivery_status FROM users u WHERE (?='' OR strpos(lower(u.email||' '||u.first_name||' '||u.last_name),lower(?))>0) AND (?::bigint IS NULL OR EXISTS(SELECT 1 FROM company_memberships filter_membership WHERE filter_membership.user_id=u.id AND filter_membership.company_id=? AND filter_membership.status='ACTIVE')) ORDER BY u.email LIMIT 100",search,search,companyId,companyId);
        if(rows.isEmpty())return rows;
        var placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
        var memberships=db.queryForList("SELECT m.user_id,c.id,c.name FROM company_memberships m JOIN companies c ON c.id=m.company_id WHERE m.status='ACTIVE' AND m.user_id IN ("+placeholders+") ORDER BY c.name,c.id",rows.stream().map(row->row.get("id")).toArray());
        var byUser=new HashMap<Long,List<Map<String,Object>>>();
        for(var membership:memberships)byUser.computeIfAbsent(((Number)membership.get("user_id")).longValue(),key->new ArrayList<>()).add(Map.of("id",membership.get("id"),"name",membership.get("name")));
        for(var row:rows)row.put("companies",byUser.getOrDefault(((Number)row.get("id")).longValue(),List.of()));
        return rows;
    }
    private void guardLastAdmin(long target){if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM role_assignments r JOIN users u ON u.id=r.user_id WHERE r.user_id=? AND r.role_key='PLATFORM_ADMIN' AND r.removed_at IS NULL AND u.is_active AND NOT u.admin_locked AND u.password_hash IS NOT NULL)",Boolean.class,target))&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM role_assignments r JOIN users u ON u.id=r.user_id WHERE r.user_id<>? AND r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.removed_at IS NULL AND u.is_active AND NOT u.admin_locked AND u.password_hash IS NOT NULL)",Boolean.class,target)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Appoint another available Platform Admin before removing this administrator's access.");}
    public void account(long target,String email,AccountAction input){
        long actor=actor(email);db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('chronos-platform-admins',0))",Object.class);access.lockAccountAdministration(target);actor(email);
        var rows=db.queryForList("SELECT id,is_active,admin_locked,password_hash IS NOT NULL activated,platform_access_version FROM users WHERE id=?",target);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");var row=rows.getFirst();if(((Number)row.get("platform_access_version")).longValue()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Account changed. Reload and try again.");
        if(target==actor&&Set.of("LOCK","DEACTIVATE","DEMOTE").contains(input.action()))throw new AccessDeniedException("Another Platform Admin must change your administrative access.");
        switch(input.action()){
            case "LOCK","DEACTIVATE","DEMOTE" -> {guardLastAdmin(target);if(!input.action().equals("DEMOTE"))access.guardAccountAdminAccessLoss(target);if(input.action().equals("LOCK"))db.update("UPDATE users SET admin_locked=true,admin_lock_reason=?,locked_at=now(),credential_version=credential_version+1 WHERE id=?",input.reason(),target);else if(input.action().equals("DEACTIVATE"))db.update("UPDATE users SET is_active=false,credential_version=credential_version+1 WHERE id=?",target);else{db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND role_key='PLATFORM_ADMIN' AND company_id IS NULL AND removed_at IS NULL",target);db.update("UPDATE users SET credential_version=credential_version+1 WHERE id=?",target);}sessions.revokeAll(target);}
            case "UNLOCK" -> db.update("UPDATE users SET admin_locked=false,admin_lock_reason=NULL,locked_until=NULL,failed_login_count=0,unlocked_by=?,unlocked_at=now() WHERE id=?",actor,target);
            case "REACTIVATE" -> db.update("UPDATE users SET is_active=true WHERE id=?",target);
            case "SIGN_OUT" -> {db.update("UPDATE users SET credential_version=credential_version+1 WHERE id=?",target);sessions.revokeAll(target);}
            case "PROMOTE" -> {if(!Boolean.TRUE.equals(row.get("is_active"))||Boolean.TRUE.equals(row.get("admin_locked"))||!Boolean.TRUE.equals(row.get("activated")))throw new IllegalArgumentException("Choose an available activated account");if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM company_memberships WHERE user_id=? AND status='ACTIVE')",Boolean.class,target)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove active company memberships before appointing a Platform Admin.");db.update("INSERT INTO role_assignments(user_id,role_key,assigned_by_user_id) VALUES (?,'PLATFORM_ADMIN',?) ON CONFLICT DO NOTHING",target,actor);db.update("UPDATE users SET credential_version=credential_version+1 WHERE id=?",target);sessions.revokeAll(target);}
            case "INVITE" -> {if(!access.hasPlatformRole(target,"PLATFORM_ADMIN"))throw new AccessDeniedException("Use company invitations to onboard company members");onboarding.invite(target);}
            case "REVOKE_INVITATION" -> {if(!access.hasPlatformRole(target,"PLATFORM_ADMIN")||Boolean.TRUE.equals(row.get("activated")))throw new IllegalArgumentException("Choose an invited Platform Admin");onboarding.revoke(target);db.update("UPDATE role_assignments SET removed_at=now() WHERE user_id=? AND role_key='PLATFORM_ADMIN' AND removed_at IS NULL",target);}
            default -> throw new IllegalArgumentException("Choose a supported account action");
        }
        db.update("UPDATE users SET platform_access_version=platform_access_version+1 WHERE id=?",target);record(actor,null,target,"ACCOUNT_"+input.action(),input.reason());
    }
    public long createAdmin(String email,AdminInput input){long actor=actor(email);db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('chronos-platform-admins',0))",Object.class);actor(email);User user=onboarding.create(input.firstName(),input.lastName(),input.email());db.update("INSERT INTO role_assignments(user_id,role_key,assigned_by_user_id) VALUES (?,'PLATFORM_ADMIN',?)",user.getId(),actor);onboarding.invite(user.getId());record(actor,null,user.getId(),"PLATFORM_ADMIN_INVITED",null);return user.getId();}
    public List<Map<String,Object>> audit(String email,int page){actor(email);if(page<0||page>100000)throw new IllegalArgumentException("Invalid page");return db.queryForList("SELECT a.*,u.email actor_email,c.name company_name,t.email target_email FROM platform_activity a LEFT JOIN users u ON u.id=a.user_id LEFT JOIN companies c ON c.id=a.company_id LEFT JOIN users t ON t.id=a.target_user_id ORDER BY a.created_at DESC,a.id DESC LIMIT 100 OFFSET ?",page*100);}
    public List<Map<String,Object>> adminInvitations(long company,String email){actor(email);return db.queryForList("SELECT i.id,i.invitee_email,i.role_key,i.created_at,i.expires_at,i.accepted_at,i.revoked_at,d.status delivery_status,d.attempts,d.sent_at,d.last_error FROM company_invitations i LEFT JOIN LATERAL (SELECT status,attempts,sent_at,last_error FROM invitation_delivery WHERE company_invitation_id=i.id ORDER BY id DESC LIMIT 1) d ON true WHERE i.company_id=? AND i.project_id IS NULL AND i.role_key='COMPANY_ADMIN' AND EXISTS(SELECT 1 FROM platform_activity p WHERE p.company_id=i.company_id AND p.user_id=i.created_by_user_id AND p.action='COMPANY_CREATED') ORDER BY i.id DESC",company);}
}
