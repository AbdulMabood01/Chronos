package com.maxwell.chronos.service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.maxwell.chronos.enums.AuditAction;
import java.util.Map;
@Service
@Transactional
@RequiredArgsConstructor
public class PlatformSettingsService {
    public static final String REMINDERS="platform.timesheet.reminders.enabled";
    private final JdbcTemplate db;
    private final CompanyAccessService access;
    private final AuditService audit;
    public record Snapshot(long version,Map<String,String> values) {}
    private void authorize(long actor){if(!Boolean.TRUE.equals(access.platformPermissions(actor).capabilities().get("canConfigurePlatform")))throw new AccessDeniedException("Platform configuration permission required");}
    public Snapshot get(long actor){authorize(actor);return snapshot();}
    private Snapshot snapshot(){return db.queryForObject("SELECT version,setting_value FROM system_settings WHERE setting_key=?",(rs,i)->new Snapshot(rs.getLong(1),Map.of(REMINDERS,rs.getString(2))),REMINDERS);}
    public Snapshot update(long actor,String key,CompanySettingsService.Input input){
        authorize(actor);
        if(!REMINDERS.equals(key))throw new IllegalArgumentException("Choose a supported platform setting");
        access.lockAccountAdministration(actor);authorize(actor);
        if(input.version()==null||input.version()<0)throw new IllegalArgumentException("Reload settings before editing");
        String value=CompanySettingsService.validatedValue(REMINDERS,input.value()).toString();
        if(db.update("UPDATE system_settings SET setting_value=?,version=version+1,updated_at=now() WHERE setting_key=? AND version=?",value,key,input.version())!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Settings changed. Reload and try again.");
        audit.logRequiredAction(actor,AuditAction.SETTINGS_UPDATED,"PlatformSettings",null,"Platform reminder delivery "+value);
        return snapshot();
    }
}
