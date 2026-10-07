package com.maxwell.chronos.service;

import com.maxwell.chronos.dto.LeaveBalanceDTO;
import com.maxwell.chronos.enums.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @Transactional @RequiredArgsConstructor
public class CompanyLeaveService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final AuditService audit;
    private final NotificationService notifications;
    public record RequestInput(@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotNull VacationType vacationType,
        @Size(max=500) String notes,@Size(max=120) String specialReason,@Min(0) Long version) {}
    public record Decision(@NotNull @Min(0) Long version,LeaveAccountingType accountingType,@Size(max=500) String reason) {}
    public record AllowanceInput(@Min(1900) @Max(9998) int year,
        @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3,fraction=2) BigDecimal vacationDays,
        @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3,fraction=2) BigDecimal sickDays,
        @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3,fraction=2) BigDecimal bereavementDays,
        @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3,fraction=2) BigDecimal addVacationDays,
        @NotNull @DecimalMin("0") @DecimalMax("366") @Digits(integer=3,fraction=2) BigDecimal addSickDays,
        @NotBlank @Size(max=500) String reason,@NotNull @Min(-1) Long version) {}
    public record PolicyInput(@Min(1900) @Max(9998) int year,@NotNull @Min(0) Long settingsVersion,
        @NotNull @Min(-1) Long policyVersion,boolean confirmed) {}
    public record Balance(long companyId,long userId,long version,int year,boolean configured,LeaveBalanceDTO.Balance vacation,
        LeaveBalanceDTO.Balance sick,LeaveBalanceDTO.Balance bereavement,String source) {}
    private static final String REQUEST_SQL="SELECT v.*,concat_ws(' ',u.first_name,u.last_name) AS user_name,"+
        "concat_ws(' ',a.first_name,a.last_name) AS approved_by_name,concat_ws(' ',r.first_name,r.last_name) AS rejected_by_name "+
        "FROM vacation_requests v JOIN users u ON u.id=v.user_id LEFT JOIN users a ON a.id=v.approved_by_id LEFT JOIN users r ON r.id=v.rejected_by_id ";
    private void member(long company,long actor){access.requireActiveCompanyAccess(company,actor);}
    private void admin(long company,long actor){access.requireCompanyCapability(company,actor,"canManageLeavePolicy");}
    private void lock(long company,long user){db.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE",Long.class,user);access.lockCompanyAdministration(company);}
    private void year(int year){if(year<1900||year>9998)throw new IllegalArgumentException("Year must be between 1900 and 9998");}
    private Map<String,Object> request(long company,long id){return db.queryForList(REQUEST_SQL+"WHERE v.company_id=? AND v.id=?",company,id).stream().findFirst()
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Company leave request not found"));}
    private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private LocalDate date(Map<String,Object> row,String key){Object v=row.get(key);return v instanceof LocalDate d?d:((java.sql.Date)v).toLocalDate();}
    private void revision(Map<String,Object> row,Long version){if(version==null||number(row,"version")!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Leave record changed. Reload and try again.");}
    private Map<String,Object> dto(Map<String,Object> row){
        var result=new LinkedHashMap<String,Object>();
        for(String field:List.of("id","version","hours","status","notes"))result.put(field,row.get(field));
        result.put("status",Objects.toString(row.get("status"),null));
        result.put("hours",BigDecimal.valueOf(weekdays(date(row,"start_date"),date(row,"end_date"))*8));
        for(var field:Map.ofEntries(Map.entry("companyId","company_id"),Map.entry("userId","user_id"),Map.entry("userName","user_name"),Map.entry("startDate","start_date"),Map.entry("endDate","end_date"),Map.entry("vacationType","vacation_type"),Map.entry("specialReason","special_reason"),Map.entry("accountingType","accounting_type"),Map.entry("submittedAt","submitted_at"),Map.entry("approvedAt","approved_at"),Map.entry("rejectedAt","rejected_at"),Map.entry("approvedByName","approved_by_name"),Map.entry("rejectedByName","rejected_by_name"),Map.entry("rejectionReason","rejection_reason"),Map.entry("createdAt","created_at")).entrySet()){
            Object value=row.get(field.getValue());if(value instanceof java.sql.Date d)value=d.toLocalDate();if(field.getKey().equals("vacationType"))value=Objects.toString(value,null);result.put(field.getKey(),value);
        }
        return result;
    }
    public List<Map<String,Object>> mine(long company,long actor){member(company,actor);return db.queryForList(REQUEST_SQL+"WHERE v.company_id=? AND v.user_id=? ORDER BY v.created_at DESC",company,actor).stream().map(this::dto).toList();}
    public List<Map<String,Object>> queue(long company,long actor){admin(company,actor);return db.queryForList(REQUEST_SQL+"WHERE v.company_id=? ORDER BY v.created_at DESC",company).stream().map(this::dto).toList();}
    public Map<String,Object> save(long company,Long id,long actor,RequestInput input){
        member(company,actor);lock(company,actor);member(company,actor);validate(input);
        if(id==null){id=db.queryForObject("INSERT INTO vacation_requests(company_id,user_id,start_date,end_date,vacation_type,hours,status,notes,special_reason) VALUES (?,?,?,?,?::vacation_type_enum,?,'DRAFT',?,?) RETURNING id",Long.class,company,actor,input.startDate(),input.endDate(),input.vacationType().name(),BigDecimal.valueOf(weekdays(input.startDate(),input.endDate())*8),input.notes(),input.specialReason());}
        else {var row=request(company,id);owner(row,actor);revision(row,input.version());editable(row);
            db.update("UPDATE vacation_requests SET start_date=?,end_date=?,vacation_type=?::vacation_type_enum,hours=?,notes=?,special_reason=?,accounting_type=null,version=version+1,updated_at=now() WHERE id=?",input.startDate(),input.endDate(),input.vacationType().name(),BigDecimal.valueOf(weekdays(input.startDate(),input.endDate())*8),input.notes(),input.specialReason(),id);}
        audit.logRequiredAction(actor,AuditAction.VACATION_EDITED,"VacationRequest",id,"Leave draft saved in company "+company);return dto(request(company,id));
    }
    private void validate(RequestInput input){
        if(input.startDate()==null||input.endDate()==null||input.vacationType()==null||input.startDate().isAfter(input.endDate())||input.startDate().getYear()<1900||input.endDate().getYear()>9998||java.time.temporal.ChronoUnit.DAYS.between(input.startDate(),input.endDate())>366)throw new IllegalArgumentException("Choose a leave date range of up to 367 days");
        if(input.notes()!=null&&input.notes().length()>500)throw new IllegalArgumentException("Leave notes cannot exceed 500 characters");
        if(input.vacationType()==VacationType.SPECIAL&&(input.specialReason()==null||input.specialReason().isBlank()||input.specialReason().length()>120))throw new IllegalArgumentException("Choose a special leave reason");
        if(input.vacationType()==VacationType.SPECIAL&&"Other".equalsIgnoreCase(input.specialReason())&&(input.notes()==null||input.notes().isBlank()))throw new IllegalArgumentException("Describe the special leave reason in Notes");
        if(!Set.of(VacationType.VACATION,VacationType.SICK,VacationType.BEREAVEMENT,VacationType.UNPAID_LEAVE,VacationType.SPECIAL).contains(input.vacationType()))throw new IllegalArgumentException("Choose a supported leave type");
    }
    private void owner(Map<String,Object> row,long actor){if(number(row,"user_id")!=actor)throw new AccessDeniedException("Only the requester can change this leave draft");}
    private void editable(Map<String,Object> row){if(!Set.of("DRAFT","REJECTED").contains(row.get("status").toString()))throw new IllegalArgumentException("Only draft or rejected leave requests can be edited");}
    private void overlap(long company,Map<String,Object> row){if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM vacation_requests WHERE company_id=? AND user_id=? AND id<>? AND status IN ('SUBMITTED','APPROVED','LOCKED') AND start_date<=? AND end_date>=?)",Boolean.class,company,number(row,"user_id"),number(row,"id"),date(row,"end_date"),date(row,"start_date"))))throw new IllegalArgumentException("These dates overlap another submitted or approved leave request");}
    public Map<String,Object> submit(long company,long id,long actor,long version){
        member(company,actor);lock(company,actor);member(company,actor);var row=request(company,id);owner(row,actor);revision(row,version);editable(row);overlap(company,row);
        db.update("UPDATE vacation_requests SET status='SUBMITTED',submitted_at=now(),version=version+1,updated_at=now(),rejection_reason=null WHERE id=?",id);
        audit.logRequiredAction(actor,AuditAction.VACATION_SUBMITTED,"VacationRequest",id,"Leave submitted in company "+company);
        for(long admin:db.queryForList("SELECT DISTINCT r.user_id FROM role_assignments r JOIN company_memberships m ON m.company_id=r.company_id AND m.user_id=r.user_id JOIN users u ON u.id=r.user_id WHERE r.company_id=? AND r.project_id IS NULL AND r.role_key='COMPANY_ADMIN' AND r.removed_at IS NULL AND m.status='ACTIVE' AND u.is_active=TRUE",Long.class,company)){
            if(admin!=actor&&!access.hasPlatformRole(admin,"PLATFORM_ADMIN"))notifications.createNotification(admin,"VACATION_SUBMITTED","Company leave request",row.get("user_name")+" submitted leave for company "+company,id,"VacationRequest");
        }
        return dto(request(company,id));
    }
    public void delete(long company,long id,long actor,long version){member(company,actor);lock(company,actor);member(company,actor);var row=request(company,id);owner(row,actor);revision(row,version);if(!Set.of("DRAFT","REJECTED","SUBMITTED").contains(row.get("status").toString()))throw new IllegalArgumentException("Approved or locked leave cannot be deleted");audit.logRequiredAction(actor,AuditAction.VACATION_EDITED,"VacationRequest",id,"Leave request deleted ("+row.get("status")+") in company "+company);db.update("DELETE FROM vacation_requests WHERE id=?",id);}
    public Map<String,Object> decide(long company,long id,long actor,boolean approve,Decision input){
        admin(company,actor);var before=request(company,id);long user=number(before,"user_id");
        if(approve)db.queryForList("SELECT id FROM timesheets WHERE company_id=? AND user_id=? ORDER BY id FOR UPDATE",company,user);
        lock(company,user);admin(company,actor);var row=request(company,id);revision(row,input.version());
        if(user==actor)throw new AccessDeniedException("You cannot approve or reject your own leave request");
        if(!"SUBMITTED".equals(row.get("status").toString()))throw new IllegalArgumentException("Only submitted leave requests can be reviewed");
        if(approve){overlap(company,row);String accounting=accounting(row,input.accountingType());validateBalance(company,row,accounting);clearDraftHours(company,row);
            db.update("UPDATE vacation_requests SET status='APPROVED',accounting_type=?,approved_by_id=?,approved_at=now(),version=version+1,updated_at=now() WHERE id=?",accounting,actor,id);
        }else{if(input.reason()==null||input.reason().isBlank())throw new IllegalArgumentException("Rejection reason is required");db.update("UPDATE vacation_requests SET status='REJECTED',rejected_by_id=?,rejected_at=now(),rejection_reason=?,version=version+1,updated_at=now() WHERE id=?",actor,input.reason().trim(),id);}
        audit.logRequiredAction(actor,approve?AuditAction.VACATION_APPROVED:AuditAction.VACATION_REJECTED,"VacationRequest",id,"Leave reviewed in company "+company);
        notifications.createNotification(user,approve?"VACATION_APPROVED":"VACATION_REJECTED",approve?"Leave approved":"Leave rejected","Your leave request in company "+company+" was reviewed.",id,"VacationRequest");return dto(request(company,id));
    }
    private String accounting(Map<String,Object> row,LeaveAccountingType selected){return switch(row.get("vacation_type").toString()){
        case "VACATION","SICK","BEREAVEMENT"->row.get("vacation_type").toString();case "UNPAID_LEAVE"->"UNPAID";
        default->{if(selected==null)throw new IllegalArgumentException("Choose how this special leave is counted before approval");yield selected.name();}};}
    private void validateBalance(long company,Map<String,Object> row,String accounting){
        if(!Set.of("VACATION","SICK","BEREAVEMENT").contains(accounting))return;
        long user=number(row,"user_id");LocalDate first=date(row,"start_date"),last=date(row,"end_date");
        for(int y=first.getYear();y<=last.getYear();y++){
            var balance=balance(company,user,y,user);if(!balance.configured())throw new IllegalArgumentException("Leave allowance is not set for "+y);
            var bucket=switch(accounting){case "SICK"->balance.sick();case "BEREAVEMENT"->balance.bereavement();default->balance.vacation();};
            LocalDate start=first.isBefore(LocalDate.of(y,1,1))?LocalDate.of(y,1,1):first,end=last.isAfter(LocalDate.of(y,12,31))?LocalDate.of(y,12,31):last;
            if(bucket.remainingDays().compareTo(BigDecimal.valueOf(weekdays(start,end)))<0)throw new IllegalArgumentException("Not enough "+accounting.toLowerCase()+" leave for "+y+"; choose unpaid leave or update the allowance");
        }
    }
    private void clearDraftHours(long company,Map<String,Object> row){
        long user=number(row,"user_id");var start=date(row,"start_date");var end=date(row,"end_date");
        db.queryForList("SELECT id FROM timesheets WHERE company_id=? AND user_id=? AND make_date(year,month,1)<=? AND (make_date(year,month,1)+interval '1 month')::date>? ORDER BY id FOR UPDATE",company,user,end,start);
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE t.company_id=? AND t.user_id=? AND e.entry_date BETWEEN ? AND ? AND e.hours>0 AND (t.status NOT IN ('DRAFT','REJECTED') OR EXISTS(SELECT 1 FROM timesheet_approval_periods a WHERE a.user_id=t.user_id AND a.project_id=e.project_id AND e.entry_date BETWEEN a.period_start AND a.period_end AND a.status NOT IN ('DRAFT','REJECTED')) OR EXISTS(SELECT 1 FROM timesheet_project_submissions s WHERE s.timesheet_id=t.id AND s.project_id=e.project_id AND s.status NOT IN ('DRAFT','REJECTED'))))",Boolean.class,company,user,start,end)))throw new IllegalArgumentException("Leave conflicts with submitted or finalized hours; reopen the timesheet first");
        db.update("DELETE FROM time_entry_sessions WHERE time_entry_id IN (SELECT e.id FROM time_entries e JOIN timesheets t ON t.id=e.timesheet_id WHERE t.company_id=? AND t.user_id=? AND e.entry_date BETWEEN ? AND ?)",company,user,start,end);
        db.update("UPDATE time_entries e SET hours=0 FROM timesheets t WHERE e.timesheet_id=t.id AND t.company_id=? AND t.user_id=? AND e.entry_date BETWEEN ? AND ?",company,user,start,end);
        db.update("UPDATE timesheets t SET total_hours=COALESCE((SELECT sum(hours) FROM time_entries WHERE timesheet_id=t.id),0) WHERE t.company_id=? AND t.user_id=?",company,user);
        db.update("UPDATE timesheet_project_submissions s SET total_hours=COALESCE((SELECT sum(e.hours) FROM time_entries e WHERE e.timesheet_id=s.timesheet_id AND e.project_id=s.project_id),0) FROM timesheets t WHERE s.timesheet_id=t.id AND t.company_id=? AND t.user_id=?",company,user);
    }
    private long weekdays(LocalDate start,LocalDate end){return FederalHolidays.workingDays(start,end);}
    private void ensureAllowance(long company,long user,int year){
        db.update("INSERT INTO company_leave_allowances(company_id,user_id,leave_year,vacation_days,sick_days,bereavement_days) SELECT m.company_id,m.user_id,p.leave_year,p.vacation_days,p.sick_days,p.bereavement_days FROM company_memberships m JOIN users u ON u.id=m.user_id JOIN company_leave_policies p ON p.company_id=m.company_id WHERE m.company_id=? AND m.user_id=? AND m.status='ACTIVE' AND u.is_active=TRUE AND NOT EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.removed_at IS NULL) AND p.leave_year=? AND (m.joining_date IS NULL OR m.joining_date<make_date(?+1,1,1)) ON CONFLICT DO NOTHING",company,user,year,year);
    }
    public Balance balance(long company,long user,int year,long actor){
        member(company,actor);if(user!=actor)admin(company,actor);year(year);
        if(db.queryForList("SELECT id FROM company_memberships WHERE company_id=? AND user_id=?",company,user).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company member not found");
        ensureAllowance(company,user,year);
        var rows=db.queryForList("SELECT * FROM company_leave_allowances WHERE company_id=? AND user_id=? AND leave_year=?",company,user,year);
        Map<String,Object> value=rows.isEmpty()?Map.of():rows.get(0);
        return new Balance(company,user,rows.isEmpty()?-1:number(value,"version"),year,!rows.isEmpty(),bucket(company,user,year,"VACATION",money(value,"vacation_days"),money(value,"extra_vacation_days")),bucket(company,user,year,"SICK",money(value,"sick_days"),money(value,"extra_sick_days")),bucket(company,user,year,"BEREAVEMENT",money(value,"bereavement_days"),BigDecimal.ZERO),rows.isEmpty()?null:(String)value.get("source"));
    }
    private BigDecimal money(Map<String,Object> row,String key){return row.get(key) instanceof BigDecimal n?n:BigDecimal.ZERO;}
    private LeaveBalanceDTO.Balance bucket(long company,long user,int year,String type,BigDecimal base,BigDecimal extra){
        var used=new HashSet<LocalDate>();var pending=new HashSet<LocalDate>();LocalDate start=LocalDate.of(year,1,1),end=LocalDate.of(year,12,31);
        db.queryForList("SELECT start_date,end_date,status FROM vacation_requests WHERE company_id=? AND user_id=? AND COALESCE(accounting_type,CASE WHEN vacation_type IN ('VACATION','SICK','BEREAVEMENT') THEN vacation_type::text END)=? AND status IN ('APPROVED','LOCKED','SUBMITTED') AND start_date<=? AND end_date>=?",company,user,type,end,start).forEach(row->{
            LocalDate first=date(row,"start_date"),last=date(row,"end_date");if(first.isBefore(start))first=start;if(last.isAfter(end))last=end;
            var holidays=FederalHolidays.between(first,last);
            first.datesUntil(last.plusDays(1)).filter(d->d.getDayOfWeek().getValue()<6&&!holidays.contains(d)).forEach(("SUBMITTED".equals(row.get("status").toString())?pending:used)::add);
        });BigDecimal spent=BigDecimal.valueOf(used.size()),total=base.add(extra);
        return new LeaveBalanceDTO.Balance(base,extra,spent,total.subtract(spent).max(BigDecimal.ZERO),spent.subtract(total).max(BigDecimal.ZERO),BigDecimal.valueOf(pending.size()));
    }
    public Balance allowance(long company,long user,long actor,AllowanceInput input){
        admin(company,actor);lock(company,user);admin(company,actor);member(company,user);var before=balance(company,user,input.year(),actor);
        if(before.version()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Leave record changed. Reload and try again.");
        db.update("INSERT INTO company_leave_allowances(company_id,user_id,leave_year,vacation_days,sick_days,bereavement_days,extra_vacation_days,extra_sick_days,source) VALUES (?,?,?,?,?,?,?,?,'OVERRIDE') ON CONFLICT (company_id,user_id,leave_year) DO UPDATE SET vacation_days=excluded.vacation_days,sick_days=excluded.sick_days,bereavement_days=excluded.bereavement_days,extra_vacation_days=company_leave_allowances.extra_vacation_days+excluded.extra_vacation_days,extra_sick_days=company_leave_allowances.extra_sick_days+excluded.extra_sick_days,source='OVERRIDE',version=company_leave_allowances.version+1",company,user,input.year(),input.vacationDays(),input.sickDays(),input.bereavementDays(),input.addVacationDays(),input.addSickDays());
        audit.logRequiredAction(actor,AuditAction.LEAVE_ALLOWANCE_UPDATED,"Company",company,"Leave allowance for member "+user+", year "+input.year()+": "+input.reason());return balance(company,user,input.year(),actor);
    }
    public Map<String,Object> preview(long company,long actor,int year){
        admin(company,actor);year(year);var settings=db.queryForMap("SELECT * FROM company_settings WHERE company_id=?",company);
        var policies=db.queryForList("SELECT version FROM company_leave_policies WHERE company_id=? AND leave_year=?",company,year);
        var result=new LinkedHashMap<String,Object>();result.put("companyId",company);result.put("year",year);result.put("settingsVersion",settings.get("version"));result.put("policyVersion",policies.isEmpty()?-1:policies.get(0).get("version"));
        result.put("vacationDays",settings.get("vacation_days"));result.put("sickDays",settings.get("sick_days"));result.put("bereavementDays",settings.get("bereavement_days"));
        var counts=db.queryForMap("SELECT count(*) FILTER(WHERE a.user_id IS NULL) AS new_allowances,count(*) FILTER(WHERE a.source='POLICY') AS policy_allowances,count(*) FILTER(WHERE a.source='OVERRIDE') AS overrides FROM company_memberships m JOIN users u ON u.id=m.user_id LEFT JOIN company_leave_allowances a ON a.company_id=m.company_id AND a.user_id=m.user_id AND a.leave_year=? WHERE m.company_id=? AND m.status='ACTIVE' AND u.is_active=TRUE AND (m.joining_date IS NULL OR m.joining_date<make_date(?+1,1,1)) AND NOT EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.removed_at IS NULL)",year,company,year);
        result.put("newAllowances",counts.get("new_allowances"));result.put("policyAllowances",counts.get("policy_allowances"));result.put("overrides",counts.get("overrides"));return result;
    }
    public Map<String,Object> publish(long company,long actor,PolicyInput input){
        admin(company,actor);access.lockCompanyAdministration(company);admin(company,actor);var preview=preview(company,actor,input.year());
        if(!input.confirmed())throw new IllegalArgumentException("Confirm the company leave policy before applying it");
        if(((Number)preview.get("settingsVersion")).longValue()!=input.settingsVersion()||((Number)preview.get("policyVersion")).longValue()!=input.policyVersion())throw new ResponseStatusException(HttpStatus.CONFLICT,"Leave policy changed. Preview it again.");
        db.update("INSERT INTO company_leave_policies(company_id,leave_year,vacation_days,sick_days,bereavement_days) VALUES (?,?,?,?,?) ON CONFLICT(company_id,leave_year) DO UPDATE SET vacation_days=excluded.vacation_days,sick_days=excluded.sick_days,bereavement_days=excluded.bereavement_days,version=company_leave_policies.version+1",company,input.year(),preview.get("vacationDays"),preview.get("sickDays"),preview.get("bereavementDays"));
        int updated=db.update("INSERT INTO company_leave_allowances(company_id,user_id,leave_year,vacation_days,sick_days,bereavement_days) SELECT m.company_id,m.user_id,p.leave_year,p.vacation_days,p.sick_days,p.bereavement_days FROM company_memberships m JOIN users u ON u.id=m.user_id JOIN company_leave_policies p ON p.company_id=m.company_id WHERE m.company_id=? AND p.leave_year=? AND m.status='ACTIVE' AND u.is_active=TRUE AND (m.joining_date IS NULL OR m.joining_date<make_date(?+1,1,1)) AND NOT EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.removed_at IS NULL) ON CONFLICT(company_id,user_id,leave_year) DO UPDATE SET vacation_days=excluded.vacation_days,sick_days=excluded.sick_days,bereavement_days=excluded.bereavement_days,version=company_leave_allowances.version+1 WHERE company_leave_allowances.source='POLICY'",company,input.year(),input.year());
        audit.logRequiredAction(actor,AuditAction.SETTINGS_UPDATED,"Company",company,"Published company leave policy for "+input.year()+", updated "+updated+" allowances; preserved overrides and extra grants");return Map.of("updated",updated,"year",input.year());
    }
    public List<Map<String,Object>> calendar(long company,long actor,int year,int month){
        member(company,actor);year(year);YearMonth period=YearMonth.of(year,month);boolean companyAdmin=access.hasCompanyRole(company,actor,"COMPANY_ADMIN");
        var permissions=access.companyPermissions(company,actor);var teams=permissions.projects().stream().filter(p->p.capabilities().get("canManageProject")||p.roles().contains("PROJECT_MANAGER")).map(p->p.projectId()).toList();
        if(!companyAdmin&&teams.isEmpty())throw new AccessDeniedException("Company or project management permission required for the team calendar");
        return db.queryForList("SELECT v.id,v.user_id,concat_ws(' ',u.first_name,u.last_name) AS user_name,v.start_date,v.end_date FROM vacation_requests v JOIN users u ON u.id=v.user_id WHERE v.company_id=? AND v.status IN ('APPROVED','LOCKED') AND v.start_date<=? AND v.end_date>=? ORDER BY v.start_date,u.last_name",company,period.atEndOfMonth(),period.atDay(1)).stream()
            .filter(row->companyAdmin||teams.stream().anyMatch(project->Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM project_assignments a WHERE a.project_id=? AND a.user_id=? AND (a.is_active=TRUE OR a.end_date IS NOT NULL) AND (a.start_date IS NULL OR a.start_date<=?) AND (a.end_date IS NULL OR a.end_date>=?))",Boolean.class,project,number(row,"user_id"),date(row,"end_date").isBefore(period.atEndOfMonth())?date(row,"end_date"):period.atEndOfMonth(),date(row,"start_date").isAfter(period.atDay(1))?date(row,"start_date"):period.atDay(1)))))
            .map(row->{var result=new LinkedHashMap<String,Object>();result.put("id",row.get("id"));result.put("userId",row.get("user_id"));result.put("userName",row.get("user_name"));result.put("startDate",date(row,"start_date"));result.put("endDate",date(row,"end_date"));return (Map<String,Object>)result;}).toList();
    }
}

