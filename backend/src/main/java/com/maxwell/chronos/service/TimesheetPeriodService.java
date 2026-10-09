package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Project;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.ProjectRepository;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.repository.TimeEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class TimesheetPeriodService {
    private final JdbcTemplate db;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final TimeEntryRepository entries;
    private final CompanyAccessService access;
    private final NotificationService notifications;

    public record Period(LocalDate start, LocalDate end, String frequency) {}
    private Project project(long id) { return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found")); }
    private User user(long id) { return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found")); }
    private LocalDate today(User user) { return LocalDate.now(ZoneId.of(user.getTimezone())); }
    private Instant cutoff(Period period, User user) { return period.end().plusDays(1).atStartOfDay(ZoneId.of(user.getTimezone())).toInstant(); }

    public Period period(long projectId, LocalDate date) {
        Project project = project(projectId);
        String frequency = db.query("SELECT frequency FROM project_approval_frequency_changes WHERE project_id=? AND effective_on<=? ORDER BY effective_on DESC LIMIT 1",
                (rs, i) -> rs.getString(1), projectId, date).stream().findFirst().orElse(project.getApprovalFrequency());
        LocalDate start = switch (frequency) {
            case "DAILY" -> date;
            case "WEEKLY" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            default -> date.withDayOfMonth(1);
        };
        LocalDate end = switch (frequency) {
            case "DAILY" -> start;
            case "WEEKLY" -> start.plusDays(6);
            default -> start.withDayOfMonth(start.lengthOfMonth());
        };
        // A scheduled frequency change begins a fresh approval period at its effective date.
        LocalDate change = db.query("SELECT effective_on FROM project_approval_frequency_changes WHERE project_id=? AND effective_on>? AND effective_on<=? ORDER BY effective_on LIMIT 1",
                (rs, i) -> rs.getDate(1).toLocalDate(), projectId, start, end).stream().findFirst().orElse(null);
        if (change != null && date.isBefore(change)) end = change.minusDays(1);
        if (change != null && !date.isBefore(change)) start = change;
        return new Period(start, end, frequency);
    }

    private Map<String,Object> ensure(long userId, long projectId, Period period) {
        Project project = project(projectId);
        db.update("INSERT INTO timesheet_approval_periods(user_id,project_id,company_id,period_start,period_end,frequency) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                userId, projectId, project.getCompanyId(), period.start(), period.end(), period.frequency());
        return db.queryForMap("SELECT * FROM timesheet_approval_periods WHERE user_id=? AND project_id=? AND period_start=? AND period_end=? FOR UPDATE",
                userId, projectId, period.start(), period.end());
    }

    private Map<String,Object> existingOrDraft(long userId, long projectId, Period period) {
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM timesheet_approval_periods WHERE user_id=? AND project_id=? AND period_start=? AND period_end=?",
                userId, projectId, period.start(), period.end());
        if (!rows.isEmpty()) return rows.getFirst();
        return new java.util.HashMap<>(Map.of("user_id", userId, "project_id", projectId,
                "company_id", project(projectId).getCompanyId(), "period_start", period.start(),
                "period_end", period.end(), "frequency", period.frequency(), "status", "DRAFT"));
    }

    private Map<String,Object> row(long id) {
        return db.query("SELECT * FROM timesheet_approval_periods WHERE id=? FOR UPDATE", (rs,i) -> {
            var m = new java.util.HashMap<String,Object>();
            var md = rs.getMetaData();
            for (int n=1;n<=md.getColumnCount();n++) m.put(md.getColumnLabel(n),rs.getObject(n));
            return m;
        }, id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Approval period not found"));
    }
    private long number(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private LocalDate date(Map<String,Object> row, String key) { Object value=row.get(key); return value instanceof LocalDate d ? d : ((java.sql.Date)value).toLocalDate(); }
    private Instant instant(Map<String,Object> row, String key) {
        Object value=row.get(key);
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        if (value instanceof OffsetDateTime t) return t.toInstant();
        return (Instant)value;
    }
    private Period period(Map<String,Object> row) { return new Period(date(row,"period_start"),date(row,"period_end"),(String)row.get("frequency")); }

    private BigDecimal hours(long userId, long projectId, Period period) {
        return db.queryForObject("SELECT COALESCE(sum(te.hours),0) FROM time_entries te JOIN timesheets t ON t.id=te.timesheet_id WHERE t.user_id=? AND te.project_id=? AND te.entry_date BETWEEN ? AND ?", BigDecimal.class,
                userId,projectId,period.start(),period.end());
    }
    private boolean eligible(long userId, long projectId, Period period) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM project_assignments a WHERE a.user_id=? AND a.project_id=? AND a.is_active=TRUE AND (a.start_date IS NULL OR a.start_date<=?) AND (a.end_date IS NULL OR a.end_date>=?))", Boolean.class,
                userId,projectId,period.end(),period.start()));
    }
    private boolean eligibleDay(long userId, long projectId, LocalDate day) {
        if (day.getDayOfWeek()==DayOfWeek.SATURDAY || day.getDayOfWeek()==DayOfWeek.SUNDAY) return false;
        if (!eligible(userId,projectId,new Period(day,day,"DAILY"))) return false;
        return !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM vacation_requests WHERE company_id=(SELECT company_id FROM projects WHERE id=?) AND user_id=? AND status IN ('APPROVED','LOCKED') AND start_date<=? AND end_date>=?)",Boolean.class,projectId,userId,day,day));
    }
    private boolean needsSubmission(long userId, long projectId, Period period) {
        for(LocalDate day=period.start();!day.isAfter(period.end());day=day.plusDays(1)) if(eligibleDay(userId,projectId,day)) return true;
        return hours(userId,projectId,period).signum()>0;
    }

    public Map<String,Object> view(long requesterId, long userId, long projectId, LocalDate day) {
        access.requireActiveCompanyAccess(access.companyId(projectId),requesterId);
        if (requesterId!=userId && !access.mayReview(projectId,requesterId,userId,false,"Queue preview")
                && !access.mayManageProject(projectId,requesterId)) throw new AccessDeniedException("Approval period access denied");
        Period period=period(projectId,day);
        Map<String,Object> row=existingOrDraft(userId,projectId,period);
        User employee=user(userId);
        Instant now=Instant.now();
        String status=(String)row.get("status");
        Instant correction=instant(row,"correction_until");
        boolean correctionOpen=correction!=null && now.isBefore(correction);
        boolean required=needsSubmission(userId,projectId,period);
        boolean open=!today(employee).isBefore(period.start()) && eligible(userId,projectId,period);
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("id",row.get("id")); result.put("userId",userId); result.put("projectId",projectId);
        result.put("companyId",row.get("company_id")); result.put("periodStart",period.start()); result.put("periodEnd",period.end());
        result.put("frequency",period.frequency()); result.put("status",status); result.put("totalHours",hours(userId,projectId,period));
        result.put("late",Boolean.TRUE.equals(row.get("is_late")) || status.equals("DRAFT") && required && now.isAfter(cutoff(period,employee)));
        result.put("submittedAt",row.get("submitted_at"));
        result.put("submissionDeadline",cutoff(period,employee));
        result.put("employeeTimezone",employee.getTimezone());
        result.put("reviewedAt",row.get("reviewed_at")); result.put("reviewComment",row.get("review_comment"));
        Long reviewerId=row.get("reviewed_by_id")==null?null:number(row,"reviewed_by_id");
        String reviewerName=reviewerId==null?null:users.findById(reviewerId).map(User::getFullName).orElse(null);
        result.put("approvedByName",status.equals("APPROVED")?reviewerName:null);
        result.put("rejectedByName",status.equals("REJECTED")?reviewerName:null);
        result.put("approvedAt",status.equals("APPROVED")?row.get("reviewed_at"):null);
        result.put("rejectionReason",status.equals("REJECTED")?row.get("review_comment"):null);
        result.put("openingActive",correctionOpen);
        result.put("pdfExportEligible",status.equals("APPROVED") && !correctionOpen);
        result.put("userName",employee.getFullName());
        result.put("projectCode",project(projectId).getCode());
        result.put("projectName",project(projectId).getName());
        result.put("userJobTitle",access.employmentJobTitle(((Number)row.get("company_id")).longValue(),userId));
        result.put("year",period.start().getYear()); result.put("month",period.start().getMonthValue());
        result.put("timesheetId",db.query("""
                SELECT t.id FROM timesheets t WHERE t.user_id=? AND t.company_id=?
                AND ((t.year=? AND t.month=?) OR EXISTS (
                    SELECT 1 FROM time_entries e WHERE e.timesheet_id=t.id AND e.project_id=? AND e.entry_date BETWEEN ? AND ?))
                ORDER BY t.year,t.month LIMIT 1
                """,(rs,i)->rs.getLong(1),userId,row.get("company_id"),period.start().getYear(),period.start().getMonthValue(),projectId,period.start(),period.end()).stream().findFirst().orElse(null));
        result.put("openingReason",row.get("opening_reason"));
        result.put("openingRequestedAt",row.get("opening_requested_at"));
        Map<String,Object> assignment=db.queryForList("SELECT COALESCE(planned_hours,0) AS planned_hours,start_date,end_date FROM project_assignments WHERE project_id=? AND user_id=?",projectId,userId)
                .stream().findFirst().orElse(Map.of());
        result.put("plannedHours",assignment.getOrDefault("planned_hours",BigDecimal.ZERO));
        result.put("assignmentStart",assignment.get("start_date") instanceof java.sql.Date d ? d.toLocalDate() : assignment.get("start_date"));
        result.put("assignmentEnd",assignment.get("end_date") instanceof java.sql.Date d ? d.toLocalDate() : assignment.get("end_date"));
        result.put("timeEntries",entries.findPeriodEntries(userId,projectId,period.start(),period.end()).stream().map(entry -> {
            var item=new java.util.LinkedHashMap<String,Object>();
            item.put("entryDate",entry.getEntryDate()); item.put("hours",entry.getHours()); item.put("notes",entry.getNotes());
            item.put("late",Boolean.TRUE.equals(row.get("is_late")) && entry.getHours().signum()>0);
            item.put("sessions",entry.getSessions().stream().map(session -> Map.of("loginTime",session.getLoginTime(),"logoutTime",session.getLogoutTime())).toList());
            return item;
        }).toList());
        result.put("correctionUntil",correction); result.put("openingStatus",row.get("opening_status"));
        result.put("openingDecisionComment",row.get("opening_decision_comment"));
        result.put("editable",requesterId==userId && open && (status.equals("DRAFT") || status.equals("REJECTED") || correctionOpen && status.equals("APPROVED")));
        result.put("reviewAllowed",requesterId!=userId && status.equals("SUBMITTED") && access.mayReview(projectId,requesterId,userId,false,"Queue preview"));
        result.put("fallbackRequired",requesterId!=userId && access.hasProjectRole(projectId,requesterId,"PROJECT_ADMIN")
                && !access.hasProjectRole(projectId,userId,"PROJECT_MANAGER") && !access.hasProjectRole(projectId,requesterId,"PROJECT_MANAGER")
                && !access.hasModeratorGrant(projectId,requesterId,false));
        result.put("requiresSubmission",required);
        return result;
    }

    public void requireEditable(long userId, long projectId, LocalDate day) {
        db.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE",Long.class,userId);
        access.lockCompanyAdministration(access.companyId(projectId));
        access.requireMaySubmit(projectId,userId,"timesheets");
        Map<String,Object> period=view(userId,userId,projectId,day);
        if (!Boolean.TRUE.equals(period.get("editable"))) throw new IllegalArgumentException("This approval period is read-only. Submitted periods must be reviewed; approved periods require an opening.");
        if ("APPROVED".equals(period.get("status"))) {
            db.update("UPDATE timesheet_approval_periods SET status='DRAFT',reviewed_at=NULL,reviewed_by_id=NULL,approved_bill_rate=NULL WHERE id=?",period.get("id"));
            event(((Number)period.get("id")).longValue(),userId,"CORRECTION_STARTED",null);
        }
    }

    public Map<String,Object> submit(long userId,long projectId,LocalDate day) {
        db.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE",Long.class,userId);
        access.lockCompanyAdministration(access.companyId(projectId));
        access.requireMaySubmit(projectId,userId,"timesheets");
        Period period=period(projectId,day);
        if(!eligible(userId,projectId,period)) throw new IllegalArgumentException("Project assignment does not cover this period");
        Map<String,Object> row=ensure(userId,projectId,period);
        if ("SUBMITTED".equals(row.get("status"))) throw new IllegalArgumentException("This approval period is already submitted and awaiting review");
        requireEditable(userId,projectId,day);
        User employee=user(userId);
        boolean late=Instant.now().isAfter(cutoff(period,employee));
        db.update("UPDATE timesheet_approval_periods SET status='SUBMITTED', submitted_at=now(), is_late=is_late OR ?, reviewed_at=NULL, reviewed_by_id=NULL, review_comment=NULL, correction_until=NULL, opening_status=NULL WHERE id=?",
                late,number(row,"id"));
        event(number(row,"id"),userId,late?"SUBMITTED_LATE":"SUBMITTED",null);
        Project project=project(projectId);
        Long reviewer=project.getProjectManager()!=null && project.getProjectManager().getId().equals(userId)
                ? project.getProjectManagerHoursApprover()==null?null:project.getProjectManagerHoursApprover().getId()
                : project.getProjectManager()==null?null:project.getProjectManager().getId();
        if(reviewer!=null && reviewer!=userId) notifications.createNotification(reviewer,"TIMESHEET_SUBMITTED","Timesheet awaiting review",
                project.getCode()+" " + period.start()+" to "+period.end(),number(row,"id"),"TimesheetApprovalPeriod");
        return view(userId,userId,projectId,day);
    }

    public List<Map<String,Object>> month(long requesterId,long userId,long projectId,LocalDate day) {
        var month=YearMonth.from(day);
        var result=new java.util.ArrayList<Map<String,Object>>();
        for(LocalDate cursor=month.atDay(1);!cursor.isAfter(month.atEndOfMonth());) {
            Period period=period(projectId,cursor);
            var snapshot=view(requesterId,userId,projectId,cursor);
            // The representative date stays in the displayed month for weeks crossing its boundary.
            snapshot.put("selectionDate",cursor);
            result.add(snapshot);
            cursor=period.end().plusDays(1);
        }
        return result;
    }

    public List<Map<String,Object>> submitBatch(long userId,long projectId,List<LocalDate> dates) {
        if(dates==null || dates.isEmpty() || dates.size()>31 || dates.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Select between 1 and 31 approval periods");
        var month=YearMonth.from(dates.getFirst());
        if(dates.stream().anyMatch(date->!YearMonth.from(date).equals(month)))
            throw new IllegalArgumentException("Select approval periods within one calendar month");
        var seen=new java.util.HashSet<Period>();
        var result=new java.util.ArrayList<Map<String,Object>>();
        for(var day:dates) {
            if(!seen.add(period(projectId,day))) throw new IllegalArgumentException("Select each approval period only once");
            result.add(submit(userId,projectId,day));
        }
        return result;
    }

    public Map<String,Object> decide(long reviewerId,long id,boolean approve,String comment,String fallbackReason) {
        Map<String,Object> row=row(id);
        long userId=number(row,"user_id"),projectId=number(row,"project_id");
        access.requireMayReview(projectId,reviewerId,userId,false,fallbackReason);
        if(!"SUBMITTED".equals(row.get("status"))) throw new IllegalArgumentException("Period is not awaiting review");
        if(!approve && (comment==null || comment.isBlank())) throw new IllegalArgumentException("Rejection reason required");
        if(comment!=null && comment.length()>500) throw new IllegalArgumentException("Review comment too long");
        if(fallbackReason!=null && fallbackReason.length()>500) throw new IllegalArgumentException("Fallback reason too long");
        if(approve) db.update("UPDATE timesheet_approval_periods SET status='APPROVED', reviewed_at=now(),reviewed_by_id=?,review_comment=?,fallback_reason=?,approved_bill_rate=(SELECT bill_rate FROM project_assignments WHERE project_id=? AND user_id=?) WHERE id=?",reviewerId,comment,fallbackReason,projectId,userId,id);
        else db.update("UPDATE timesheet_approval_periods SET status='REJECTED', reviewed_at=now(),reviewed_by_id=?,review_comment=?,fallback_reason=?,approved_bill_rate=NULL,correction_until=now()+interval '7 days' WHERE id=?",reviewerId,comment,fallbackReason,id);
        event(id,reviewerId,approve?"APPROVED":"REJECTED",
                (comment==null?"":comment) + (fallbackReason==null?"":"; Project Admin fallback: "+fallbackReason.trim()));
        notifications.createNotification(userId,"TIMESHEET_REVIEWED",approve?"Timesheet approved":"Timesheet rejected",
                project(projectId).getCode()+" " + period(row).start()+" to "+period(row).end(),id,"TimesheetApprovalPeriod");
        return view(reviewerId,userId,projectId,period(row).start());
    }

    public Map<String,Object> requestOpening(long userId,long projectId,LocalDate day,String reason) {
        access.requireMaySubmit(projectId,userId,"timesheets");
        if(reason==null || reason.isBlank() || reason.length()>500) throw new IllegalArgumentException("Reason required (500 characters maximum)");
        Period period=period(projectId,day);
        if(!eligible(userId,projectId,period)) throw new IllegalArgumentException("Project assignment does not cover this period");
        Map<String,Object> row=ensure(userId,projectId,period);
        LocalDate today=today(user(userId));
        boolean approved="APPROVED".equals(row.get("status"));
        if(!approved) throw new IllegalArgumentException("Only approved periods require an opening; draft and rejected periods can be edited directly");
        if(!today.isAfter(period.end()) || today.isAfter(period.end().plusDays(30)))
            throw new IllegalArgumentException("Reopening is available within 30 days after the approval period closes");
        if("SUBMITTED".equals(row.get("status")) || "PENDING".equals(row.get("opening_status")))
            throw new IllegalArgumentException("This approval period is already awaiting review");
        db.update("UPDATE timesheet_approval_periods SET opening_requested_at=now(),opening_reason=?,opening_status='PENDING' WHERE id=?",reason.trim(),number(row,"id"));
        event(number(row,"id"),userId,"OPENING_REQUESTED",reason.trim());
        return view(userId,userId,projectId,day);
    }

    public Map<String,Object> decideOpening(long adminId,long id,boolean approve,String comment) {
        Map<String,Object> row=row(id);
        long userId=number(row,"user_id"),projectId=number(row,"project_id");
        if(adminId==userId || !access.mayManageProject(projectId,adminId)) throw new AccessDeniedException("Another Project Admin is required");
        if(!"PENDING".equals(row.get("opening_status"))) throw new IllegalArgumentException("No pending reopening request");
        if (!approve && (comment==null || comment.isBlank())) throw new IllegalArgumentException("Reason required when declining a reopening");
        if(comment!=null && comment.length()>500) throw new IllegalArgumentException("Opening comment too long");
        db.update("UPDATE timesheet_approval_periods SET opening_status=?,opening_decision_comment=?,correction_until=? WHERE id=?",
                approve?"APPROVED":"DECLINED",comment,approve?java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(7))):null,id);
        event(id,adminId,approve?"OPENING_APPROVED":"OPENING_DECLINED",comment);
        notifications.createNotification(userId,"TIMESHEET_REOPENING",approve?"Timesheet reopened":"Reopening declined",
                comment==null?"Your reopening request was reviewed":comment,id,"TimesheetApprovalPeriod");
        return view(adminId,userId,projectId,period(row).start());
    }

    public List<Map<String,Object>> pending(long reviewerId) {
        return db.queryForList("SELECT id,user_id,project_id,period_start FROM timesheet_approval_periods WHERE status='SUBMITTED' ORDER BY submitted_at")
                .stream().filter(row->access.mayReview(number(row,"project_id"),reviewerId,number(row,"user_id"),false,"Queue preview"))
                .map(row->view(reviewerId,number(row,"user_id"),number(row,"project_id"),date(row,"period_start"))).toList();
    }
    public List<Map<String,Object>> pendingOpenings(long adminId) {
        return db.queryForList("SELECT id,user_id,project_id,period_start FROM timesheet_approval_periods WHERE opening_status='PENDING' ORDER BY opening_requested_at")
                .stream().filter(row->access.mayManageProject(number(row,"project_id"),adminId))
                .map(row->view(adminId,number(row,"user_id"),number(row,"project_id"),date(row,"period_start"))).toList();
    }

    public boolean needsSubmissionForReminder(long userId,long projectId,Period period) {
        Map<String,Object> row=ensure(userId,projectId,period);
        return "DRAFT".equals(row.get("status")) && needsSubmission(userId,projectId,period);
    }
    public boolean markReminder(long userId,long projectId,Period period,boolean late) {
        Map<String,Object> row=ensure(userId,projectId,period);
        if(!"DRAFT".equals(row.get("status")) || !needsSubmission(userId,projectId,period)) return false;
        String column=late?"late_email_sent_at":"reminder_sent_at";
        return db.update("UPDATE timesheet_approval_periods SET "+column+"=now() WHERE id=? AND "+column+" IS NULL",number(row,"id"))==1;
    }

    private void event(long periodId,long actorId,String kind,String comment) {
        db.update("INSERT INTO timesheet_approval_period_events(period_id,actor_id,event,comment) VALUES (?,?,?,?)",periodId,actorId,kind,comment);
    }
    public List<Map<String,Object>> history(long requesterId,long periodId) {
        Map<String,Object> row=row(periodId);
        view(requesterId,number(row,"user_id"),number(row,"project_id"),period(row).start());
        return db.queryForList("SELECT e.event,e.comment,e.created_at,concat_ws(' ',u.first_name,u.last_name) AS actor_name FROM timesheet_approval_period_events e JOIN users u ON u.id=e.actor_id WHERE e.period_id=? ORDER BY e.created_at,e.id",periodId);
    }
}
