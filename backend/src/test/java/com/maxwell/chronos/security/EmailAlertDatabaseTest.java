package com.maxwell.chronos.security;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="chronos.email.integration", matches="true")
@SpringBootTest(properties={"chronos.jwtSigningKey=Q2hyb25vcy1kZXZlbG9wbWVudC1rZXktMzItYnl0ZXMtbWluaW11bQ==",
    "chronos.email-alerts.enabled=false", "spring.mail.host=", "chronos.mail.from=", "logging.level.org.springframework=INFO"})
@AutoConfigureMockMvc
@Transactional
class EmailAlertDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired EmailAlertService alerts;
    @Autowired FeedbackReviewService feedback;
    @Autowired NotificationService notifications;
    @Autowired EmployeeReportService reports;
    User employee, other, admin;
    @BeforeEach void setup() { employee=createUser("EMPLOYEE"); other=createUser("EMPLOYEE"); admin=createUser("ADMIN"); }
    private User createUser(String role) {
        String unique=UUID.randomUUID().toString();
        db.update("INSERT INTO users(employee_id,first_name,last_name,email,role,is_active,entra_id,password_hash,profile_completed) VALUES (?,?,?,?,?::user_role_enum,true,?,?,true)",
            unique,"Email",unique,unique+"@example.invalid",role,unique,"hash");
        return users.findByEmail(unique+"@example.invalid").orElseThrow();
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(User u) {
        return jwt().jwt(j -> j.subject(u.getEntraId()).claim("preferred_username",u.getEmail()));
    }
    private List<Map<String,Object>> jobs(User user) {
        return db.queryForList("SELECT * FROM email_alert_outbox WHERE user_id=? ORDER BY id",user.getId());
    }
    @Test void preferencesArePersistentAndScopedToAuthenticatedUser() throws Exception {
        mvc.perform(get("/notifications/email-preferences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notifications/email-preferences").with(token(employee)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(put("/notifications/email-preferences").with(token(employee)).contentType("application/json")
            .content("{\"enabled\":false,\"announcements\":true,\"timesheets\":true,\"vacation\":true,\"letters\":true,\"reports\":true,\"feedback\":false,\"performance\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedback").value(false));
        mvc.perform(get("/notifications/email-preferences").with(token(employee))).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/notifications/email-preferences").with(token(other))).andExpect(jsonPath("$.enabled").value(true));
    }
    @Test void optingOutSuppressesEmailsButPreservesInAppNotifications() {
        alerts.save(employee.getId(),new EmailAlertService.Preferences(false,true,true,true,true,true,true,true));
        notifications.createNotification(employee.getId(),"TIMESHEET_APPROVED","Approved","Timesheet approved",null,"Timesheet");
        assertTrue(jobs(employee).isEmpty());
        assertEquals(1,notifications.getUserNotifications(employee.getId()).size());
        notifications.createNotification(other.getId(),"VACATION_REJECTED","Rejected","Vacation rejected",null,"VacationRequest");
        assertEquals("VACATION",jobs(other).getFirst().get("category"));
    }
    @Test void anonymousFeedbackEmailsContainNoAuthorOrFeedbackTextAndHonorCategoryChoice() {
        feedback.submit(other.getEmail(),new FeedbackReviewService.Feedback(employee.getId(),"Private feedback content",null,true));
        var job=jobs(employee).getFirst();
        assertEquals("FEEDBACK",job.get("category"));
        assertFalse(job.toString().contains(other.getEmail()));
        assertFalse(job.toString().contains("Private feedback content"));
        assertTrue(jobs(other).isEmpty());
        alerts.save(employee.getId(),new EmailAlertService.Preferences(true,true,true,true,true,true,false,true));
        feedback.submit(other.getEmail(),new FeedbackReviewService.Feedback(employee.getId(),"Another feedback",null,false));
        assertEquals(1,jobs(employee).size());
    }
    @Test void performanceDraftsStayPrivateAndPublishedEditsGenerateAlerts() {
        var input=new FeedbackReviewService.Review(employee.getId(),2026,3,"Confidential summary",null,null,null,null,null,0);
        UUID id=feedback.save(admin.getEmail(),null,input);
        assertTrue(jobs(employee).isEmpty());
        feedback.publish(admin.getEmail(),id,0);
        assertEquals(1,jobs(employee).size());
        feedback.save(admin.getEmail(),id,new FeedbackReviewService.Review(employee.getId(),2026,3,"Updated summary",null,null,null,null,null,1));
        assertEquals(2,jobs(employee).size());
        assertFalse(jobs(employee).toString().contains("summary"));
    }

    @Test void announcementsWaitForPublicationAndQueueEachVersionOnce() {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO company_announcements(id,title,content,publish_date,priority,status,created_by) VALUES (?,?,?,CURRENT_DATE + 1,'NORMAL','PUBLISHED',?)",
            id,"Test announcement","Content stays in the app",admin.getId());
        var provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        var dispatcher=new EmailAlertDispatcher(db,alerts,provider,"","http://localhost:5173");
        dispatcher.deliver();
        long baseline=jobs(employee).stream().filter(j -> j.get("category").equals("ANNOUNCEMENTS")).count();
        assertEquals(-1,db.queryForObject("SELECT emailed_version FROM company_announcements WHERE id=?",Integer.class,id));
        db.update("UPDATE company_announcements SET publish_date=CURRENT_DATE WHERE id=?",id);
        dispatcher.deliver();
        assertEquals(baseline+1,jobs(employee).stream().filter(j -> j.get("category").equals("ANNOUNCEMENTS")).count());
        dispatcher.deliver();
        assertEquals(baseline+1,jobs(employee).stream().filter(j -> j.get("category").equals("ANNOUNCEMENTS")).count());
        db.update("UPDATE company_announcements SET version=version+1 WHERE id=?",id);
        dispatcher.deliver();
        assertEquals(baseline+2,jobs(employee).stream().filter(j -> j.get("category").equals("ANNOUNCEMENTS")).count());
    }

    @Test void reportEmailsRespectAnonymousReportingAndHrAudience() {
        var receipt=reports.submit(employee.getEmail(),new EmployeeReportService.Submission(
            EmployeeReportService.Category.OTHER_INCIDENT,"Private subject","Private description",null,null,null,null,true,true),List.of());
        assertTrue(jobs(employee).isEmpty());
        assertTrue(jobs(other).isEmpty());
        assertEquals("REPORTS",jobs(admin).getFirst().get("category"));
        reports.review(admin.getEmail(),receipt.reportId(),new EmployeeReportService.Review(EmployeeReportService.Status.UNDER_REVIEW,"Private note",null,null));
        assertTrue(jobs(employee).isEmpty());
        assertFalse(jobs(admin).toString().contains("Private"));
        var named=reports.submit(employee.getEmail(),new EmployeeReportService.Submission(
            EmployeeReportService.Category.OTHER_INCIDENT,"Private subject","Private description",null,null,null,null,false,true),List.of());
        reports.review(admin.getEmail(),named.reportId(),new EmployeeReportService.Review(EmployeeReportService.Status.UNDER_REVIEW,null,null,null));
        assertEquals(1,jobs(employee).size());
        assertTrue(jobs(employee).getFirst().get("subject").toString().contains(named.reportId().toString()));
        assertTrue(jobs(other).isEmpty());
    }
}
