package com.maxwell.chronos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in against configured PostgreSQL; test records roll back after each test. */
@EnabledIfSystemProperty(named="chronos.reports.integration", matches="true")
@SpringBootTest(properties={"chronos.jwtSigningKey=Q2hyb25vcy1kZXZlbG9wbWVudC1rZXktMzItYnl0ZXMtbWluaW11bQ==", "logging.level.org.springframework=INFO"})
@AutoConfigureMockMvc
@Transactional
class EmployeeReportDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    User employee, admin;
    @BeforeEach void setup() {
        employee = createUser("EMPLOYEE"); admin = createUser("SUPER_ADMIN");
    }
    private User createUser(String role) {
        String unique = UUID.randomUUID().toString();
        db.update("INSERT INTO users(employee_id,first_name,last_name,email,role,is_active,entra_id,password_hash,profile_completed) VALUES (?,?,?,?,?::user_role_enum,true,?,?,true)",
            unique, "Report", "Test", unique + "@example.invalid", role, unique, "test-hash");
        return users.findByEmail(unique + "@example.invalid").orElseThrow();
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(User user) {
        return jwt().jwt(j -> j.subject(user.getEntraId()).claim("preferred_username", user.getEmail()));
    }
    @Test void completeConfidentialLifecyclePersistsAndEnforcesAccess() throws Exception {
        var report = new MockMultipartFile("report", "", "application/json", "{\"category\":\"SEXUAL_HARASSMENT\",\"subject\":\"Integration concern\",\"description\":\"Confidential details\",\"anonymous\":true,\"privacyAcknowledged\":true}".getBytes());
        var file = new MockMultipartFile("attachments", "evidence.txt", "text/plain", "evidence".getBytes());
        String receipt = mvc.perform(multipart("/employee-reports").file(report).file(file).with(token(employee)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(receipt).get("reportId").asText());
        assertNull(db.queryForMap("SELECT reporter_id FROM employee_reports WHERE id=?", id).get("reporter_id"));
        assertNull(db.queryForMap("SELECT actor_id FROM employee_report_history WHERE report_id=?", id).get("actor_id"));
        mvc.perform(get("/employee-reports/" + id).with(token(employee))).andExpect(status().isForbidden());
        String details = mvc.perform(get("/employee-reports/" + id).with(token(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.reporter").doesNotExist()).andExpect(jsonPath("$.reporter_id").doesNotExist())
            .andExpect(jsonPath("$.anonymous").value(true)).andReturn().getResponse().getContentAsString();
        String attachment = json.readTree(details).get("attachments").get(0).get("id").asText();
        mvc.perform(get("/employee-reports/" + id + "/attachments/" + attachment).with(token(employee))).andExpect(status().isForbidden());
        mvc.perform(get("/employee-reports/" + id + "/attachments/" + attachment).with(token(admin)))
            .andExpect(status().isOk()).andExpect(content().bytes("evidence".getBytes()))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/employee-reports/" + UUID.randomUUID() + "/attachments/" + attachment).with(token(admin))).andExpect(status().isNotFound());
        for (String status : new String[]{"UNDER_REVIEW","INVESTIGATION","RESOLVED","CLOSED"}) {
            mvc.perform(patch("/employee-reports/" + id).with(token(admin)).contentType("application/json")
                .content("{\"status\":\"" + status + "\",\"note\":\"Internal note\",\"actionsTaken\":\"Reviewed evidence\",\"resolution\":\"Follow-up recorded\"}"))
                .andExpect(status().isOk());
        }
        mvc.perform(get("/employee-reports").param("category","SEXUAL_HARASSMENT").param("status","CLOSED")
            .param("from","2000-01-01").param("to","2099-01-01").with(token(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].status").value(org.hamcrest.Matchers.hasItem("CLOSED")));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM employee_report_history WHERE report_id=?", Integer.class, id));
        var tracked = mvc.perform(get("/employee-reports/" + id).with(token(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.updated_at").exists()).andExpect(jsonPath("$.history[0].status").value("SUBMITTED"))
            .andExpect(jsonPath("$.history[3].status").value("UNDER_REVIEW"))
            .andExpect(jsonPath("$.history[3].first_name").value(admin.getFirstName()))
            .andReturn().getResponse().getContentAsString();
        assertFalse(tracked.contains(employee.getEmail()));
        mvc.perform(get("/employee-reports").param("reportId", id.toString().substring(0,8)).param("anonymous", "true").with(token(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].submitted_by").value("Anonymous"));
        mvc.perform(get("/employee-reports").param("reportId", id.toString()).param("anonymous", "false").with(token(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void identifiedSubmissionAppearsImmediatelyWithReporterAndFilters() throws Exception {
        var report = new MockMultipartFile("report", "", "application/json", "{\"category\":\"SAFETY_CONCERN\",\"subject\":\"Safety issue\",\"description\":\"Details\",\"anonymous\":false,\"privacyAcknowledged\":true}".getBytes());
        var response = mvc.perform(multipart("/employee-reports").file(report).with(token(employee)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("reportId").asText();
        mvc.perform(get("/employee-reports").param("reportId",id).param("anonymous","false").param("category","SAFETY_CONCERN").with(token(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].submitted_by").value("Report Test"))
            .andExpect(jsonPath("$[0].status").value("SUBMITTED")).andExpect(jsonPath("$[0].updated_at").exists());
        mvc.perform(get("/employee-reports/" + id).with(token(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.reporter.email").value(employee.getEmail()));
        mvc.perform(get("/employee-reports").param("reportId", "%' OR true--").with(token(admin))).andExpect(status().isBadRequest());
    }
    @Test void legacyAnonymousFilesHideFilenamesAndUnsafeDownloadsAreDenied() throws Exception {
        UUID id = UUID.randomUUID(), file = UUID.randomUUID();
        db.update("INSERT INTO employee_reports(id,category,subject,description,anonymous) VALUES (?,?,?,?,true)", id,"OTHER_INCIDENT","Old report","Details");
        db.update("INSERT INTO employee_report_attachments(id,report_id,filename,content) VALUES (?,?,?,?)", file,id,"private-employee.docx",new byte[]{1,2});
        String detail = mvc.perform(get("/employee-reports/" + id).with(token(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.attachments[0].downloadAvailable").value(false)).andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains("private-employee"));
        mvc.perform(get("/employee-reports/" + id + "/attachments/" + file).with(token(admin))).andExpect(status().isBadRequest());
        db.update("UPDATE employee_report_attachments SET filename=?,content=? WHERE id=?", "private-employee.txt", "Evidence".getBytes(), file);
        mvc.perform(get("/employee-reports/" + id + "/attachments/" + file).with(token(admin))).andExpect(status().isOk())
            .andExpect(content().bytes("Evidence".getBytes()))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-employee"))));
    }
    @Test void databaseRejectsIdentityLinkedToAnonymousReport() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update(
            "INSERT INTO employee_reports(id,category,subject,description,anonymous,reporter_id) VALUES (?,?,?,?,true,?)",
            UUID.randomUUID(), "OTHER_INCIDENT", "Test", "Details", employee.getId()));
    }
}
