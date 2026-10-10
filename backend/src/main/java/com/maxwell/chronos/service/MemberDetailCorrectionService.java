package com.maxwell.chronos.service;
import com.maxwell.chronos.enums.AuditAction;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @Transactional @RequiredArgsConstructor
public class MemberDetailCorrectionService {
 private final JdbcTemplate db;private final CompanyAccessService access;private final UserRepository users;private final AuditService audit;
 private boolean admin(long company,long actor){return Boolean.TRUE.equals(access.companyPermissions(company,actor).capabilities().get("canManageCompanyPeople"));}
 private void member(long company,long user){if(access.hasPlatformRole(user,"PLATFORM_ADMIN")||!access.hasActiveCompanyAccess(company,user))throw new AccessDeniedException("Active company membership required");}
 private void locks(long company,long target){access.lockAccountAdministration(target);access.lockCompanyAdministration(company);}
 public List<Map<String,Object>> list(long company,long target,long actor){member(company,actor);member(company,target);if(actor!=target&&!admin(company,actor))throw new AccessDeniedException("Company Admin permission required");return db.queryForList("SELECT id,kind,reason,status,requested_by,decided_by,decision_reason,created_at FROM member_detail_corrections WHERE company_id=? AND target_user_id=? ORDER BY id DESC LIMIT 50",company,target);}
 public long request(long company,long target,long actor,String kind,String reason){
  if(!Set.of("PROFILE","EMPLOYMENT").contains(kind))throw new IllegalArgumentException("Choose profile or employment correction");
  if(reason==null||reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("Enter a correction reason of up to 1000 characters");
  locks(company,target);member(company,actor);member(company,target);if(actor!=target&&!admin(company,actor))throw new AccessDeniedException("Company Admin permission required");
  if(kind.equals("PROFILE")&&actor!=target)throw new AccessDeniedException("Employees request their own personal profile corrections");
  boolean locked=kind.equals("PROFILE")?db.queryForObject("SELECT (profile_completed OR profile_details_submitted) AND NOT profile_correction_open FROM users WHERE id=?",Boolean.class,target):db.queryForObject("SELECT employment_locked FROM company_memberships WHERE company_id=? AND user_id=?",Boolean.class,company,target);
  if(!locked)throw new ResponseStatusException(HttpStatus.CONFLICT,"These details are already open for editing");
  long id=db.queryForObject("INSERT INTO member_detail_corrections(company_id,target_user_id,requested_by,kind,reason) VALUES (?,?,?,?,?) RETURNING id",Long.class,company,target,actor,kind,reason.trim());
  audit.logRequiredAction(actor,AuditAction.USER_PROFILE_UPDATED,"Company",company,"Requested "+kind+" correction "+id+" for member "+target);return id;
 }
 public void decide(long company,long target,long id,long actor,boolean approve,String reason){
  if(reason==null||reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("Enter a decision reason of up to 1000 characters");
  locks(company,target);member(company,actor);member(company,target);if(!admin(company,actor))throw new AccessDeniedException("Company Admin permission required");
  var rows=db.queryForList("SELECT * FROM member_detail_corrections WHERE id=? AND company_id=? AND target_user_id=? FOR UPDATE",id,company,target);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Correction request not found");var r=rows.getFirst();
  if(!r.get("status").equals("PENDING"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Correction request already decided");
  if(actor==target||actor==((Number)r.get("requested_by")).longValue())throw new AccessDeniedException("Another Company Admin must decide this correction request");
  if(approve){if(r.get("kind").equals("PROFILE")){var u=users.findForUpdate(target).orElseThrow();if(u.isProfileCorrectionOpen())throw new ResponseStatusException(HttpStatus.CONFLICT,"Profile is already open for correction");u.setProfileCorrectionOpen(true);users.save(u);}else{if(db.update("UPDATE company_memberships SET employment_locked=false,employment_version=employment_version+1 WHERE company_id=? AND user_id=? AND employment_locked",company,target)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Employment details are already open for editing");}}
  db.update("UPDATE member_detail_corrections SET status=?,decided_by=?,decision_reason=?,decided_at=now() WHERE id=?",approve?"APPROVED":"REJECTED",actor,reason.trim(),id);
  audit.logRequiredAction(actor,AuditAction.USER_PROFILE_UPDATED,"Company",company,(approve?"Approved":"Rejected")+" correction "+id+" for member "+target+"; reason: "+reason.trim());
 }
}
