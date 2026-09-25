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
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="chronos.announcements.integration",matches="true")
@SpringBootTest(properties={"chronos.jwtSigningKey=Q2hyb25vcy1kZXZlbG9wbWVudC1rZXktMzItYnl0ZXMtbWluaW11bQ==","logging.level.org.springframework=INFO"})
@AutoConfigureMockMvc
@Transactional
class AnnouncementDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    User employee, admin;
    @BeforeEach void setup() { employee=create("EMPLOYEE"); admin=create("ADMIN"); }
    User create(String role) {
        String unique=UUID.randomUUID().toString();
        db.update("INSERT INTO users(employee_id,first_name,last_name,email,role,is_active,entra_id,password_hash,profile_completed) VALUES (?,?,?,?,?::user_role_enum,true,?,?,true)",unique,"Person",unique,unique+"@example.invalid",role,unique,"hash");
        return users.findByEmail(unique+"@example.invalid").orElseThrow();
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor token(User u) { return jwt().jwt(j -> j.subject(u.getEntraId()).claim("preferred_username",u.getEmail())); }
    Map<String,Object> input(String state,LocalDate publish,LocalDate expires) {
        var in=new HashMap<String,Object>(Map.of("title","News","content","Company update","publishDate",publish.toString(),"priority","IMPORTANT","status",state,"acknowledgmentRequired",true,"version",0,"attachmentName","hello.txt","attachmentBase64","SGVsbG8="));
        if(expires!=null) in.put("expirationDate",expires.toString());
        return in;
    }
    UUID save(Map<String,Object> in) throws Exception {
        var result=mvc.perform(post("/announcements").with(token(admin)).contentType("application/json").content(json.writeValueAsString(in))).andExpect(status().isOk()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).asText());
    }
    @Test void hiddenAnnouncementsCannotBeOpenedAcknowledgedOrDownloaded() throws Exception {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(var in:List.of(input("DRAFT",today,null),input("ARCHIVED",today,null),input("PUBLISHED",today.plusDays(1),null),input("PUBLISHED",today.minusDays(2),today.minusDays(1)))) {
            UUID id=save(in);
            mvc.perform(post("/announcements/"+id+"/open").with(token(employee))).andExpect(status().isNotFound());
            mvc.perform(post("/announcements/"+id+"/acknowledge").param("version","0").with(token(employee))).andExpect(status().isNotFound());
            mvc.perform(get("/announcements/"+id+"/attachment").with(token(employee))).andExpect(status().isNotFound());
            mvc.perform(post("/announcements/"+id+"/open").param("management","true").with(token(admin))).andExpect(status().isOk());
            var feed=mvc.perform(get("/announcements").with(token(employee))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(feed.contains(id.toString()));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM announcement_receipts WHERE announcement_id=?",Integer.class,id));
        }
    }
    @Test void lifecycleTracksUniqueEmployeesRejectsStaleChangesAndPreservesArchiveTracking() throws Exception {
        var in=input("PUBLISHED",LocalDate.now(ZoneOffset.UTC),LocalDate.now(ZoneOffset.UTC));
        UUID id=save(in);
        mvc.perform(get("/announcements").with(token(employee))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM announcement_receipts WHERE announcement_id=?",Integer.class,id));
        for(int n=0;n<2;n++) {
            mvc.perform(post("/announcements/"+id+"/open").with(token(employee))).andExpect(status().isOk());
            mvc.perform(post("/announcements/"+id+"/acknowledge").param("version","0").with(token(employee))).andExpect(status().isOk());
        }
        mvc.perform(get("/announcements/"+id+"/tracking").with(token(admin))).andExpect(jsonPath("$.viewed").value(1)).andExpect(jsonPath("$.acknowledged").value(1));
        mvc.perform(get("/announcements/"+id+"/attachment").with(token(employee))).andExpect(content().bytes("Hello".getBytes())).andExpect(header().string("X-Content-Type-Options","nosniff"));
        in.put("content","Revised update"); in.remove("attachmentBase64");
        mvc.perform(put("/announcements/"+id).with(token(admin)).contentType("application/json").content(json.writeValueAsString(in))).andExpect(status().isOk());
        mvc.perform(post("/announcements/"+id+"/acknowledge").param("version","0").with(token(employee))).andExpect(status().isConflict());
        mvc.perform(put("/announcements/"+id).with(token(admin)).contentType("application/json").content(json.writeValueAsString(in))).andExpect(status().isConflict());
        mvc.perform(get("/announcements/"+id+"/tracking").with(token(admin))).andExpect(jsonPath("$.viewed").value(0)).andExpect(jsonPath("$.acknowledged").value(0));
        mvc.perform(post("/announcements/"+id+"/acknowledge").param("version","1").with(token(employee))).andExpect(status().isOk());
        mvc.perform(post("/announcements/"+id+"/status").param("version","1").param("status","ARCHIVED").with(token(admin))).andExpect(status().isOk());
        mvc.perform(get("/announcements/"+id+"/tracking").with(token(admin))).andExpect(jsonPath("$.acknowledged").value(1));
        mvc.perform(delete("/announcements/"+id).param("version","2").with(token(admin))).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM announcement_receipts WHERE announcement_id=?",Integer.class,id));
    }
}
