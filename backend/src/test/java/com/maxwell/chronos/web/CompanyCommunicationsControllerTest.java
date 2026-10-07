package com.maxwell.chronos.web;
import com.maxwell.chronos.config.SecurityConfig;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CompanyAnnouncementController.class,CompanyFeedbackReviewController.class,CompanyEmployeeReportController.class,CompanyLetterRequestController.class,CompanyGovernanceController.class}) @Import(SecurityConfig.class)
class CompanyCommunicationsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean UserRepository users;
    @MockitoBean CompanyAnnouncementService announcements;
    @MockitoBean CompanyFeedbackReviewService feedback;
    @MockitoBean CompanyEmployeeReportService reports;
    @MockitoBean CompanyLetterRequestService letters;
    @MockitoBean CompanyWorkflowAccess flow;
    User actor;
    @BeforeEach void setup(){actor=User.builder().id(7L).email("member@example.com").role(UserRole.EMPLOYEE).isActive(true).entraId("subject").passwordHash("hash").build();when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));when(sessions.valid(nullable(String.class),anyLong())).thenReturn(true);when(flow.member(12,actor.getEmail())).thenReturn(actor);}
    org.springframework.test.web.servlet.request.RequestPostProcessor token(){return jwt().jwt(j->j.subject("subject").claim("preferred_username",actor.getEmail()).claim("role","ADMIN").claim("companyId",13));}
    @Test void pathCompanyAndServerActorOverrideForgedClaimsAndBodyFields() throws Exception {
        mvc.perform(post("/companies/12/feedback-reviews/feedback").with(token()).contentType("application/json").content("{\"employeeId\":8,\"content\":\"Feedback\",\"senderId\":99,\"companyId\":13}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(feedback).submit(eq(12L),eq(actor.getEmail()),argThat(in->in.employeeId()==8L&&in.content().equals("Feedback")));
        mvc.perform(post("/companies/12/letter-requests/9/approve").with(token()).contentType("application/json").content("{\"version\":4,\"fullName\":\"Member\",\"jobTitle\":\"Engineer\",\"employmentStartDate\":\"2025-01-01\",\"actorId\":99}")).andExpect(status().isOk());
        verify(letters).approveLetterRequest(eq(12L),eq(9L),eq(7L),any(),eq(4L));
    }
    @Test void missingAndNegativeRevisionsAndOversizedOrInvalidInputsAreRejected() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(patch("/companies/12/employee-reports/"+id).with(token()).contentType("application/json").content("{\"status\":\"UNDER_REVIEW\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/companies/12/letter-requests/9/reject").with(token()).contentType("application/json").content("{\"version\":-1,\"reason\":\"No\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/companies/12/announcements").with(token()).contentType("application/json").content("{\"title\":\"\",\"content\":\"News\",\"publishDate\":\"2026-10-01\",\"priority\":\"NORMAL\",\"status\":\"DRAFT\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/companies/12/sensitive-grants").with(token()).contentType("application/json").content("{\"userId\":8,\"permission\":\"PLATFORM_ADMIN\",\"startsOn\":\"2026-10-01\",\"endsOn\":\"2026-10-31\",\"purpose\":\"Duty\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(announcements,reports,letters);
    }
    @Test void anonymousSubmissionReturnsReceiptOnlyAndDoesNotSerializeSensitiveSubmission() throws Exception {
        UUID id=UUID.randomUUID();when(reports.submit(eq(12L),eq(actor.getEmail()),any(),any())).thenReturn(new CompanyEmployeeReportService.Receipt(id,"SUBMITTED"));
        var body=new MockMultipartFile("report","","application/json","{\"category\":\"OTHER_INCIDENT\",\"subject\":\"Concern\",\"description\":\"Private\",\"anonymous\":true,\"privacyAcknowledged\":true,\"excludedUserIds\":[8]}".getBytes());
        mvc.perform(multipart("/companies/12/employee-reports").file(body).with(token())).andExpect(status().isOk()).andExpect(jsonPath("$.reportId").value(id.toString())).andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.reporter_id").doesNotExist()).andExpect(jsonPath("$.description").doesNotExist());
        verify(reports).submit(eq(12L),eq(actor.getEmail()),argThat(in->in.anonymous()&&in.excludedUserIds().equals(List.of(8L))&&in.toString().contains("REDACTED")),nullable(List.class));
    }
    @Test void caseAndGrantDenialsStayForbiddenEvenWithAdminJwtClaim() throws Exception {
        UUID id=UUID.randomUUID();when(reports.detail(12,actor.getEmail(),id)).thenThrow(new AccessDeniedException("Explicit grant required"));when(flow.grants(12,actor.getEmail())).thenThrow(new AccessDeniedException("Company Admin required"));
        mvc.perform(get("/companies/12/employee-reports/"+id).with(token())).andExpect(status().isForbidden());mvc.perform(get("/companies/12/sensitive-grants").with(token())).andExpect(status().isForbidden());
        mvc.perform(get("/companies/12/audit")).andExpect(status().isUnauthorized());
    }
    @Test void attachmentsAreUncachedDownloadsAndGrantMutationsUseRevision() throws Exception {
        UUID id=UUID.randomUUID(),file=UUID.randomUUID();when(reports.download(12,actor.getEmail(),id,file)).thenReturn(new CompanyEmployeeReportService.Download("evidence.txt","file".getBytes()));
        mvc.perform(get("/companies/12/employee-reports/"+id+"/attachments/"+file).with(token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(delete("/companies/12/sensitive-grants/"+id).param("version","3").with(token())).andExpect(status().isOk());verify(flow).revoke(12,actor.getEmail(),id,3);
    }
}
