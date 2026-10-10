package com.maxwell.chronos.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;

@Service
public class BillingLifecycleJob {
    private final JdbcTemplate db;private final CompanyBillingService billing;private final CompanyEntitlements entitlements;
    private final TransactionTemplate tx;private final JavaMailSender mail;private final String sender;
    public BillingLifecycleJob(JdbcTemplate db,CompanyBillingService billing,CompanyEntitlements entitlements,org.springframework.transaction.PlatformTransactionManager manager,JavaMailSender mail,@Value("${chronos.mail.from:no-reply@chronos.local}") String sender){this.db=db;this.billing=billing;this.entitlements=entitlements;this.tx=new TransactionTemplate(manager);this.mail=mail;this.sender=sender;}
    @Scheduled(fixedDelayString="${billing.lifecycle-delay-ms:60000}",initialDelayString="${billing.lifecycle-delay-ms:60000}")
    public void run(){
        billing.reconcile();
        for(long company:db.queryForList("SELECT id FROM companies",Long.class))tx.executeWithoutResult(status->{
            entitlements.lock(company);var s=entitlements.state(company);
            db.update("UPDATE companies SET plan_tier=?,project_limit=?,team_limit=? WHERE id=? AND (plan_tier<>? OR project_limit<>? OR team_limit<>?)",s.plan(),s.projects(),s.capacity(),company,s.plan(),s.projects(),s.capacity());
            if(s.source().equals("FREE")){long days=Duration.between(Instant.now(),s.endsAt()).toDays();for(int notice:new int[]{30,7,1,0})if(days<=notice&&days>=-7)billing.queue(company,"free-"+s.endsAt()+"-notice-"+notice,"Chronos Free access "+(notice==0?"expired":"expiry reminder"),"Free access ends "+s.endsAt()+". Upgrade in Billing to continue new work. History, exports and pending reviews remain available. Records are not automatically deleted.");}
            for(var term:db.queryForList("SELECT t.id,t.ends_at FROM company_billing_terms t WHERE t.company_id=? AND t.status='ACTIVE' AND t.source IN ('PAID','TRIAL') AND t.ends_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM company_billing_terms n WHERE n.company_id=t.company_id AND n.id<>t.id AND n.status='ACTIVE' AND n.source='PAID' AND n.starts_at<=t.ends_at AND n.ends_at>t.ends_at)",company)){
                Instant end=CompanyEntitlements.instant(term.get("ends_at"));long days=Duration.between(Instant.now(),end).toDays();
                for(int notice:new int[]{30,7,1,0})if(days<=notice&&days>=-7)billing.queue(company,"term-"+term.get("id")+"-notice-"+notice,"Chronos company plan "+(notice==0?"expired":"renewal reminder"),"Your company term ends "+end+". Renewal is manual; no automatic charge. Review company capacity and renew in Billing. Authorized history and exports are retained.");
            }
        });
        // Persisted retries and current role checks prevent sending billing data to former admins.
        for(var delivery:db.queryForList("SELECT id,company_id,recipient_user_id FROM company_billing_delivery WHERE sent_at IS NULL AND attempts<5 AND next_attempt_at<=now() ORDER BY id LIMIT 30")){
            tx.executeWithoutResult(status->{var rows=db.queryForList("SELECT d.*,u.email FROM company_billing_delivery d JOIN users u ON u.id=d.recipient_user_id WHERE d.id=? AND d.sent_at IS NULL AND d.next_attempt_at<=now() FOR UPDATE OF d SKIP LOCKED",delivery.get("id"));if(rows.isEmpty())return;var d=rows.getFirst();
                try{billing.actor(((Number)d.get("company_id")).longValue(),(String)d.get("email"));var message=new SimpleMailMessage();message.setFrom(sender);message.setTo((String)d.get("email"));message.setSubject((String)d.get("subject"));message.setText((String)d.get("message"));mail.send(message);db.update("UPDATE company_billing_delivery SET sent_at=now(),attempts=attempts+1 WHERE id=?",d.get("id"));}
                catch(org.springframework.security.access.AccessDeniedException ex){db.update("UPDATE company_billing_delivery SET sent_at=now() WHERE id=?",d.get("id"));}
                catch(RuntimeException ex){db.update("UPDATE company_billing_delivery SET attempts=attempts+1,next_attempt_at=now()+interval '15 minutes' WHERE id=?",d.get("id"));}
            });
        }
    }
}
