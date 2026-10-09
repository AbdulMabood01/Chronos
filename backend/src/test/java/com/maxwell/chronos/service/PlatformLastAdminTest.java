package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.ScopedPermissions;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PlatformLastAdminTest {
 @Test void otherUnavailableOrInvitedAdminsCannotAllowLossOfLastEffectiveAdmin(){
  var db=mock(JdbcTemplate.class);var access=mock(CompanyAccessService.class);var users=mock(UserRepository.class);when(users.findByEmail("operator@example.com")).thenReturn(Optional.of(User.builder().id(8L).build()));when(access.platformPermissions(8)).thenReturn(new ScopedPermissions.Platform(List.of("PLATFORM_ADMIN"),Map.of("canConfigurePlatform",true)));when(db.queryForObject(contains("FROM users WHERE id=?"),eq(Boolean.class),eq(8L))).thenReturn(true);when(db.queryForList(contains("platform_access_version FROM users"),eq(7L))).thenReturn(List.of(Map.of("platform_access_version",0L,"is_active",true,"activated",true)));when(db.queryForObject(contains("r.user_id=?"),eq(Boolean.class),eq(7L))).thenReturn(true);when(db.queryForObject(contains("r.user_id<>?"),eq(Boolean.class),eq(7L))).thenReturn(false);
  var service=new PlatformAdministrationService(db,access,users,mock(OnboardingService.class),mock(AuthSessionService.class));assertEquals(409,assertThrows(ResponseStatusException.class,()->service.account(7,"operator@example.com",new PlatformAdministrationService.AccountAction("DEMOTE",0L,"Role ended"))).getStatusCode().value());verify(db,never()).update(contains("removed_at=now()"),anyLong());
 }
}
