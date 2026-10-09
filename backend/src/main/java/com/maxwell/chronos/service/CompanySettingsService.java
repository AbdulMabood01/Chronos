package com.maxwell.chronos.service;

import com.maxwell.chronos.enums.AuditAction;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanySettingsService {
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final AuditService audit;
    public record Input(@NotNull @Size(max=150) String value,@NotNull @Min(0) Long version) {}
    public record Snapshot(long companyId,long version,Instant updatedAt,Map<String,String> values) {}
    private static final Map<String,String> COLUMNS=Map.of("company_name","company_name",
        "vacation_days_per_year","vacation_days","sick_days_per_year","sick_days",
        "bereavement_days_per_year","bereavement_days","timesheet.reminders.enabled","reminders_enabled");

    public Snapshot get(long companyId,long actorId) {
        access.requireCompanyCapability(companyId,actorId,"canManageCompanySettings");
        return snapshot(companyId);
    }
    private Snapshot snapshot(long companyId) {
        var rows=db.query("SELECT * FROM company_settings WHERE company_id=?",(rs,i)->new Snapshot(companyId,
            rs.getLong("version"),rs.getTimestamp("updated_at").toInstant(),Map.of(
                "company_name",rs.getString("company_name"),"vacation_days_per_year",rs.getBigDecimal("vacation_days").stripTrailingZeros().toPlainString(),
                "sick_days_per_year",rs.getBigDecimal("sick_days").stripTrailingZeros().toPlainString(),
                "bereavement_days_per_year",rs.getBigDecimal("bereavement_days").stripTrailingZeros().toPlainString(),
                "timesheet.reminders.enabled",String.valueOf(rs.getBoolean("reminders_enabled")))),companyId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company settings not found");
        return rows.get(0);
    }
    public Snapshot update(long companyId,long actorId,String key,Input input) {
        access.requireCompanyCapability(companyId,actorId,"canManageCompanySettings");
        access.lockCompanyAdministration(companyId);
        access.requireCompanyCapability(companyId,actorId,"canManageCompanySettings");
        String column=COLUMNS.get(key);
        if(column==null)throw new IllegalArgumentException("Choose a supported company setting");
        if(input.version()==null||input.version()<0)throw new IllegalArgumentException("Reload settings before editing");
        Object value=validatedValue(key,input.value());
        int count=db.update("UPDATE company_settings SET "+column+"=?,version=version+1,updated_at=now() WHERE company_id=? AND version=?",value,companyId,input.version());
        if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Settings changed. Reload and try again.");
        audit.logRequiredAction(actorId,AuditAction.SETTINGS_UPDATED,"Company",companyId,"Company setting updated: "+key);
        return snapshot(companyId);
    }
    static Object validatedValue(String key,String value) {
        if(value==null)throw new IllegalArgumentException("Enter a setting value");
        value=value.trim();
        if(key.equals("company_name")){
            if(value.isEmpty()||value.length()>150||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Enter a company display name of 1 to 150 characters");
            return value;
        }
        if(key.endsWith("reminders.enabled")){
            if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Choose true or false for reminders");
            return Boolean.valueOf(value);
        }
        if(!value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Leave days must be between 0 and 366 with at most two decimal places");
        var days=new java.math.BigDecimal(value);
        if(days.compareTo(java.math.BigDecimal.valueOf(366))>0)throw new IllegalArgumentException("Leave days must be between 0 and 366 with at most two decimal places");
        return days;
    }
}
