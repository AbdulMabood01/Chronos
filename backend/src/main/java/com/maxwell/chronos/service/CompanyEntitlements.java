package com.maxwell.chronos.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class CompanyEntitlements {
    private final JdbcTemplate db;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public CompanyEntitlements(JdbcTemplate db){this(db,Clock.systemUTC());}
    public CompanyEntitlements(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    public record State(String plan,String source,UUID termId,int projects,int includedUsers,int extraSeats,
        Instant startsAt,Instant endsAt,boolean grace,boolean restricted,long activeUsers,long reservations,long openProjects,long refundPendingSeats){
        public long capacity(){return (long)includedUsers+extraSeats;}
        public long available(){return Math.max(0,capacity()-activeUsers-reservations-refundPendingSeats);}
    }
    public static Instant instant(Object value){if(value==null)return null;if(value instanceof Instant i)return i;if(value instanceof OffsetDateTime d)return d.toInstant();return ((java.sql.Timestamp)value).toInstant();}
    public Map<String,Object> term(long company){return db.queryForList("SELECT * FROM company_billing_terms WHERE company_id=? AND status='ACTIVE' AND starts_at<=? AND (ends_at IS NULL OR ends_at>?) ORDER BY CASE source WHEN 'PAID' THEN 0 WHEN 'COMPLIMENTARY' THEN 1 WHEN 'CONTRACT' THEN 2 WHEN 'TRIAL' THEN 3 ELSE 4 END,starts_at DESC LIMIT 1",company,java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant())).stream().findFirst().orElse(null);}
    public State state(long company){
        var t=term(company);String plan=t==null?"FREE":(String)t.get("plan_key"),source=t==null?"FREE":(String)t.get("source");
        boolean all=plan.equals("FREE")||source.equals("LEGACY");
        long users=count("SELECT count(*) FROM company_memberships WHERE company_id=? AND status='ACTIVE' AND (? OR workforce_enabled)",company,all);
        long pending=count("SELECT count(DISTINCT lower(i.invitee_email)) FROM company_invitations i WHERE i.company_id=? AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>? AND (? OR i.role_key IN ('USER','PROJECT_MANAGER','MODERATOR')) AND NOT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=i.company_id AND m.status='ACTIVE' AND lower(u.email)=lower(i.invitee_email) AND (? OR m.workforce_enabled))",company,java.sql.Timestamp.from(clock.instant()),all,all);
        long projects=count("SELECT count(*) FROM projects WHERE company_id=? AND status NOT IN ('COMPLETED','ARCHIVED')",company);
        int pl=t==null?1:((Number)t.get("project_limit")).intValue(),included=t==null?7:((Number)t.get("included_users")).intValue(),extra=t==null?0:((Number)t.get("extra_seats")).intValue();
        boolean over=users+pending>(long)included+extra||projects>pl,grace=false;
        Instant starts=t==null?null:instant(t.get("starts_at")),ends=t==null?null:instant(t.get("ends_at"));
        if(t==null){var old=db.queryForList("SELECT ends_at FROM company_billing_terms WHERE company_id=? AND status='ACTIVE' AND source IN ('PAID','TRIAL') AND ends_at<=? ORDER BY ends_at DESC LIMIT 1",company,java.sql.Timestamp.from(clock.instant()));if(!old.isEmpty()){ends=instant(old.getFirst().get("ends_at"));grace=clock.instant().isBefore(ends.plus(Duration.ofDays(7)));}}
        long refundPending=t==null?0:count("SELECT COALESCE(sum(extra_seats),0) FROM company_billing_purchases WHERE company_id=? AND term_id=? AND kind='SEATS' AND status='REFUND_PENDING'",company,t.get("id"));
        return new State(plan,source,t==null?null:(UUID)t.get("id"),pl,included,extra,starts,ends,grace,over&&!grace,users,pending,projects,refundPending);
    }
    private long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    public void lock(long company){if(db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,company).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found");}
    private void full(){throw new ResponseStatusException(HttpStatus.CONFLICT,"Company capacity reached. Ask a Company Admin to add seats or upgrade in Billing. Invitations never charge automatically.");}
    public void requireProjectSlot(long company){lock(company);var s=state(company);if(s.openProjects()>=s.projects())throw new ResponseStatusException(HttpStatus.CONFLICT,"Open-project limit reached. Complete or archive a project, or upgrade the company plan in Billing.");}
    public void requirePerson(long company,String email,boolean workforce){
        lock(company);var s=state(company);boolean all=s.plan().equals("FREE")||s.source().equals("LEGACY");if(!all&&!workforce)return;
        boolean occupied=count("SELECT count(*) FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=? AND m.status='ACTIVE' AND lower(u.email)=lower(?) AND (? OR m.workforce_enabled)",company,email,all)>0;
        if(occupied)return; // Assigning another role never consumes an additional company seat.
        boolean reserved=count("SELECT count(*) FROM company_invitations WHERE company_id=? AND lower(invitee_email)=lower(?) AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at>? AND (? OR role_key IN ('USER','PROJECT_MANAGER','MODERATOR'))",company,email,java.sql.Timestamp.from(clock.instant()),all)>0;
        long needed=s.activeUsers()+s.reservations()+(occupied||reserved?0:1);if(needed>s.capacity()-s.refundPendingSeats())full();
    }
    public void enroll(long company,long user){lock(company);var email=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,user);requirePerson(company,email,true);db.update("UPDATE company_memberships SET workforce_enabled=TRUE WHERE company_id=? AND user_id=? AND status='ACTIVE'",company,user);}
    public void requireWork(long company,long user){
        var s=state(company);if(s.restricted())throw new ResponseStatusException(HttpStatus.CONFLICT,"This company exceeds its current allowance. Billing, authorized history, exports and pending reviews remain available. Renew or reduce usage before submitting new work.");
        if(count("SELECT count(*) FROM company_memberships WHERE company_id=? AND user_id=? AND status='ACTIVE' AND workforce_enabled",company,user)==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ask a Company Admin to enable employee access in Billing before using employee workflows.");
    }
}
