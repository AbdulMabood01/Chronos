package com.maxwell.chronos.service;
import com.maxwell.chronos.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.*;

/** Durable invitation delivery. Tokens are encrypted at rest and never returned by status APIs. */
@Service @Transactional
public class InvitationDeliveryService {
    private final JdbcTemplate db;
    private final InvitationEmailService mail;
    private final UserRepository users;
    private final SecretKey encryptionKey;
    private final SecureRandom random=new SecureRandom();
    public InvitationDeliveryService(JdbcTemplate db,InvitationEmailService mail,UserRepository users,SecretKey key){
        this.db=db;this.mail=mail;this.users=users;
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update("chronos-invitation-delivery-v1".getBytes(StandardCharsets.UTF_8));encryptionKey=new SecretKeySpec(digest.digest(key.getEncoded()),"AES");}catch(GeneralSecurityException ex){throw new IllegalStateException("Invitation encryption unavailable");}
    }
    private byte[] encrypt(String token){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryptionKey,new GCMParameterSpec(128,iv));byte[] data=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));byte[] result=Arrays.copyOf(iv,Math.addExact(iv.length,data.length));System.arraycopy(data,0,result,iv.length,data.length);return result;}catch(GeneralSecurityException ex){throw new IllegalStateException("Invitation encryption unavailable");}}
    private String decrypt(byte[] data)throws GeneralSecurityException{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey,new GCMParameterSpec(128,Arrays.copyOf(data,12)));return new String(cipher.doFinal(Arrays.copyOfRange(data,12,data.length)),StandardCharsets.UTF_8);}
    public void company(long invitation,String token){
        db.update("UPDATE invitation_delivery SET status='CANCELLED',encrypted_token=NULL WHERE company_invitation_id=? AND status IN ('PENDING','FAILED')",invitation);
        db.update("INSERT INTO invitation_delivery(company_invitation_id,token_hash,encrypted_token) VALUES (?,?,?)",invitation,OnboardingService.hash(token),encrypt(token));
    }
    public void account(long user,String token){
        db.update("UPDATE invitation_delivery SET status='CANCELLED',encrypted_token=NULL WHERE employee_id=? AND status IN ('PENDING','FAILED')",user);
        db.update("INSERT INTO invitation_delivery(employee_id,token_hash,encrypted_token) VALUES (?,?,?)",user,OnboardingService.hash(token),encrypt(token));
    }
    @Scheduled(fixedDelayString="${chronos.invitation-delivery.poll-ms:30000}")
    public void deliver(){
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('chronos-invitation-delivery',0))",Object.class);
        var pending=db.queryForList("SELECT id,company_invitation_id,employee_id FROM invitation_delivery WHERE status='PENDING' AND available_at<=now() ORDER BY id LIMIT 20");
        // Acquire all account locks before all company locks, even for mixed delivery batches.
        var employees=pending.stream().map(r->r.get("employee_id")).filter(Objects::nonNull).map(v->((Number)v).longValue()).distinct().sorted().toList();
        for(long employee:employees)db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,employee);
        var companies=new TreeSet<Long>();for(var row:pending)if(row.get("company_invitation_id")!=null)companies.addAll(db.queryForList("SELECT company_id FROM company_invitations WHERE id=?",Long.class,row.get("company_invitation_id")));
        for(long company:companies)db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,company);
        for(var candidate:pending){
            // Account/company locks precede invitation and delivery locks, matching invitation acceptance/resend.
            if(candidate.get("employee_id") instanceof Number employee)db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,employee.longValue());
            else{var ids=db.queryForList("SELECT company_id FROM company_invitations WHERE id=?",Long.class,candidate.get("company_invitation_id"));if(!ids.isEmpty())db.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE",Long.class,ids.getFirst());}
            var locked=db.queryForList("SELECT * FROM invitation_delivery WHERE id=? AND status='PENDING' AND available_at<=now() FOR UPDATE",candidate.get("id"));if(locked.isEmpty())continue;
            var row=locked.getFirst();var invitations=row.get("employee_id") instanceof Number employee
                ?db.queryForList("SELECT i.token_hash,i.expires_at,u.email FROM employee_invitations i JOIN users u ON u.id=i.employee_id WHERE i.employee_id=? AND i.used_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>now() AND u.is_active AND NOT u.admin_locked AND u.password_hash IS NULL",employee.longValue())
                :db.queryForList("SELECT i.token_hash,i.expires_at,i.invitee_email email,c.name FROM company_invitations i JOIN companies c ON c.id=i.company_id WHERE i.id=? AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>now() AND NOT c.is_suspended AND NOT EXISTS(SELECT 1 FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE m.company_id=i.company_id AND lower(u.email)=i.invitee_email AND m.status='REMOVED') AND NOT EXISTS(SELECT 1 FROM users u JOIN role_assignments r ON r.user_id=u.id WHERE lower(u.email)=i.invitee_email AND r.role_key='PLATFORM_ADMIN' AND r.removed_at IS NULL)",row.get("company_invitation_id"));
            if(invitations.isEmpty() || !Objects.equals(invitations.getFirst().get("token_hash"),row.get("token_hash"))){db.update("UPDATE invitation_delivery SET status='CANCELLED',encrypted_token=NULL WHERE id=?",row.get("id"));continue;}
            var invitation=invitations.getFirst();
            try{
                String token=decrypt((byte[])row.get("encrypted_token"));if(!OnboardingService.hash(token).equals(row.get("token_hash")))throw new GeneralSecurityException("Invalid delivery token");Object expiry=invitation.get("expires_at");Instant expires=expiry instanceof OffsetDateTime offset?offset.toInstant():((java.sql.Timestamp)expiry).toInstant();
                if(row.get("employee_id") instanceof Number employee)mail.send(users.findById(employee.longValue()).orElseThrow(),token,expires);
                else mail.sendCompanyInvitation((String)invitation.get("email"),(String)invitation.get("name"),token,expires);
                db.update("UPDATE invitation_delivery SET status='SENT',sent_at=now(),attempts=attempts+1,last_error=NULL,encrypted_token=NULL WHERE id=?",row.get("id"));
            }catch(GeneralSecurityException|RuntimeException ex){
                // No provider error, address, credential or token is logged or exposed through the UI.
                db.update("UPDATE invitation_delivery SET attempts=attempts+1,status=CASE WHEN attempts>=4 THEN 'FAILED' ELSE 'PENDING' END,available_at=now()+interval '5 minutes',last_error='Delivery unavailable. Check email configuration or resend the invitation.' WHERE id=?",row.get("id"));
            }
        }
    }
}
