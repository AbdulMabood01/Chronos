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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="chronos.feedback.integration", matches="true")
@SpringBootTest(properties={"chronos.jwtSigningKey=Q2hyb25vcy1kZXZlbG9wbWVudC1rZXktMzItYnl0ZXMtbWluaW11bQ==", "logging.level.org.springframework=INFO"})
@AutoConfigureMockMvc
@Transactional
class FeedbackReviewDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    User sender, recipient, admin;
    @BeforeEach void setup() { sender=createUser("EMPLOYEE"); recipient=createUser("EMPLOYEE"); admin=createUser("ADMIN"); }
    private User createUser(String role) {
        String unique=UUID.randomUUID().toString();
        db.update("INSERT INTO users(employee_id,first_name,last_name,email,role,is_active,entra_id,password_hash,profile_completed) VALUES (?,?,?,?,?::user_role_enum,true,?,?,true)", unique,"Person",unique,unique+"@example.invalid",role,unique,"hash");
        return users.findByEmail(unique+"@example.invalid").orElseThrow();
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(User u) { return jwt().jwt(j -> j.subject(u.getEntraId()).claim("preferred_username",u.getEmail())); }
    @Test void anonymousFeedbackIsHiddenFromRecipientButPresentInSenderHistory() throws Exception {
        for (boolean anonymous : List.of(true,false)) {
            mvc.perform(post("/feedback-reviews/feedback").with(token(sender)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("employeeId",recipient.getId(),"content","Helpful feedback","anonymous",anonymous,"category","APPRECIATION")))).andExpect(status().isOk());
        }
        String received=mvc.perform(get("/feedback-reviews/feedback").with(token(recipient))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        var records=json.readTree(received);
        assertEquals(2,records.size());
        for (var record : records) {
            assertFalse(record.has("sender_id")); assertFalse(record.has("sender_email"));
            if (record.get("anonymous").asBoolean()) assertFalse(record.hasNonNull("sender_name"));
            else assertEquals(sender.getFullName(),record.get("sender_name").asText());
        }
        assertFalse(received.contains(sender.getEmail()));
        mvc.perform(get("/feedback-reviews/feedback").param("given","true").with(token(sender))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].recipient_email").value(recipient.getEmail()));
        mvc.perform(get("/feedback-reviews/feedback").param("given","true").with(token(recipient)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/feedback-reviews/feedback").param("given","false").with(token(sender)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/feedback-reviews/feedback").with(token(admin))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/feedback-reviews/employees").param("query",recipient.getEmail()).with(token(sender)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(recipient.getId()));
    }
    @Test void reviewLifecycleProtectsDraftsRecordsEditsAndRejectsStaleUpdates() throws Exception {
        Map<String,Object> input=new HashMap<>(Map.of("employeeId",recipient.getId(),"year",2026,"quarter",3,"summary","Initial summary","version",0));
        String result=mvc.perform(post("/feedback-reviews/reviews").with(token(admin)).contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(result).asText();
        mvc.perform(get("/feedback-reviews/reviews").with(token(recipient))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/feedback-reviews/reviews").param("employeeId",recipient.getId().toString()).with(token(admin))).andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].published_at").doesNotExist());
        mvc.perform(post("/feedback-reviews/reviews/"+id+"/publish").param("version","0").with(token(admin))).andExpect(status().isOk());
        mvc.perform(get("/feedback-reviews/reviews").with(token(recipient))).andExpect(jsonPath("$[0].summary").value("Initial summary"));
        mvc.perform(get("/feedback-reviews/reviews").with(token(sender))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/feedback-reviews/reviews/"+id).with(token(admin)).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        input.put("version",1); input.put("summary","Updated summary");
        mvc.perform(put("/feedback-reviews/reviews/"+id).with(token(admin)).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isOk());
        mvc.perform(get("/feedback-reviews/reviews").with(token(recipient))).andExpect(jsonPath("$[0].summary").value("Updated summary"));
        mvc.perform(get("/feedback-reviews/reviews/"+id+"/audit").with(token(admin))).andExpect(jsonPath("$.length()").value(3));
        assertEquals("Initial summary",db.queryForObject("SELECT snapshot->>'summary' FROM performance_review_audit WHERE review_id=? AND action='CREATED'",String.class,UUID.fromString(id)));
        // Duplicate quarter is enforced in PostgreSQL (last operation because a SQL violation aborts this test transaction).
        mvc.perform(post("/feedback-reviews/reviews").with(token(admin)).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
    }
}
