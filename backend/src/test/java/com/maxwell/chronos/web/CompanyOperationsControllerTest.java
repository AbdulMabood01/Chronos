package com.maxwell.chronos.web;
import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CompanyLeaveController.class,CompanyReportController.class,VacationController.class}) @Import(SecurityConfig.class)
class CompanyOperationsControllerTest{
    @Autowired MockMvc mvc;
    @MockitoBean CompanyLeaveService leave;
    @MockitoBean ReportService reports;
    @MockitoBean CompanyAccessService access;
    @MockitoBean JdbcTemplate db;
    @MockitoBean UserService users;
    @MockitoBean UserRepository repository;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void setup(){var actor=User.builder().id(5L).email("actor@example.com").entraId("subject").role(UserRole.EMPLOYEE).isActive(true).passwordHash("test-hash").build();when(users.findUserEntityByEmail(actor.getEmail())).thenReturn(actor);when(repository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));when(sessions.valid(nullable(String.class),eq(5L))).thenReturn(true);}
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(b->b.subject("subject").claim("preferred_username","actor@example.com").claim("employee_id",999));}
    @Test void unauthenticatedAndForgedActorRequestsAreHandledByServerIdentity()throws Exception{
        mvc.perform(get("/companies/12/leave/my")).andExpect(status().isUnauthorized());
        mvc.perform(post("/companies/12/leave/requests").with(token()).contentType("application/json").content("{\"companyId\":13,\"userId\":999,\"startDate\":\"2026-10-06\",\"endDate\":\"2026-10-06\",\"vacationType\":\"VACATION\"}")) .andExpect(status().isOk());
        verify(leave).save(eq(12L),isNull(),eq(5L),any());
    }
    @Test void decisionsAndPolicyPublicationRequireVersions()throws Exception{
        mvc.perform(post("/companies/12/leave/requests/7/approve").with(token()).contentType("application/json").content("{}")) .andExpect(status().isBadRequest());
        mvc.perform(post("/companies/12/leave/policy/apply").with(token()).contentType("application/json").content("{\"year\":2026,\"confirmed\":true}")) .andExpect(status().isBadRequest());verifyNoInteractions(leave);
    }
    @Test void roleDenialsPropagateAs403()throws Exception{
        when(leave.queue(12,5)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));mvc.perform(get("/companies/12/leave/requests").with(token())).andExpect(status().isForbidden());
    }
    @Test void mixedCompanyExportsAreRejectedBeforeBuildingTheArchive()throws Exception{
        when(db.queryForList(anyString(),eq(Long.class),eq(7L),eq(12L))).thenReturn(List.of(21L));
        when(db.queryForList(anyString(),eq(Long.class),eq(8L),eq(12L))).thenReturn(List.of());when(reports.canReadProjectReport(21,5)).thenReturn(true);
        mvc.perform(get("/companies/12/reports/timesheet-periods/export").param("ids","7,8").with(token())).andExpect(status().isForbidden());verify(reports,never()).exportApprovalPeriods(anyList(),anyLong());
    }
    @Test void globalLeaveEndpointsAreRetired()throws Exception{
        mvc.perform(get("/vacation/my").with(token())).andExpect(status().isGone());mvc.perform(post("/approvals/vacation/7/approve").with(token())).andExpect(status().isGone());verifyNoInteractions(leave);
    }
}
