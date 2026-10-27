package com.municipal.rbac;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class AuditContractTest {
  @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
  String admin;
  @BeforeEach void login()throws Exception{
    admin="Bearer "+body(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"account\":\"admin\",\"password\":\"admin123\"}")).andExpect(status().isOk())).get("data").get("token").asText();
  }
  JsonNode body(ResultActions result)throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString());}
  JsonNode page(String params)throws Exception{return body(mvc.perform(get("/api/authorization-logs"+params).header("Authorization",admin)).andExpect(status().isOk())).get("data");}
  void seed(){for(int i=0;i<4;i++)db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,occurred_at,request_id) VALUES(1,'audit-test','audit-test','read',?,'2026-01-01 00:00:00',?)",i%2,"case-"+i);}
  @Test void envelopeForObjectArrayAndVoidAndStableErrors()throws Exception{
    for(String path:List.of("/api/me","/api/roles")){
      var result=mvc.perform(get(path).header("Authorization",admin)).andExpect(status().isOk()).andReturn();
      var data=json.readTree(result.getResponse().getContentAsString());assertEquals("OK",data.get("code").asText());assertTrue(data.has("data"));assertEquals(result.getResponse().getHeader("X-Request-Id"),data.get("requestId").asText());
    }
    mvc.perform(post("/api/auth/logout")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK")).andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mvc.perform(get("/api/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    mvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/api/organizations/companies").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(db.queryForMap("SELECT code,name FROM companies LIMIT 1")))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
  }
  @Test void keysetSnapshotDoesNotRepeatOrIncludeNewRowsAndBindsFilters()throws Exception{
    seed();String filter="?appCode=audit-test&limit=2";
    var first=page(filter);assertTrue(first.get("hasMore").asBoolean());assertEquals(2,first.get("items").size());
    db.update("INSERT INTO authorization_logs(username,app_code,permission_code,allowed) VALUES('later','audit-test','read',1)");
    var second=page(filter+"&cursor="+first.get("nextCursor").asText());assertFalse(second.get("hasMore").asBoolean());assertEquals(2,second.get("items").size());
    Set<Long> ids=new HashSet<>();for(var row:first.get("items"))ids.add(row.get("id").asLong());for(var row:second.get("items"))assertTrue(ids.add(row.get("id").asLong()));assertEquals(4,ids.size());
    mvc.perform(get("/api/authorization-logs").header("Authorization",admin).param("limit","3").param("cursor",first.get("nextCursor").asText())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    for(String raw:List.of("!","",Base64.getUrlEncoder().encodeToString("-1:1:x".getBytes()),Base64.getUrlEncoder().encodeToString("1:0:x".getBytes()),Base64.getUrlEncoder().encodeToString("1:2:x".getBytes())))mvc.perform(get("/api/authorization-logs").header("Authorization",admin).param("cursor",raw)).andExpect(status().isBadRequest());
  }
  @Test void allFiltersAndUtcHalfOpenBoundsAndArchiveBatches()throws Exception{
    seed();
    assertEquals(2,page("?userId=1&username=audit-test&appCode=audit-test&allowed=true&event=AUTHORIZATION&from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:01Z&storage=ACTIVE").get("items").size());
    assertEquals(0,page("?username=audit-test&to=2026-01-01T00:00:00Z").get("items").size());
    assertEquals(2,page("?username=audit-test&allowed=false").get("items").size());
    assertEquals(4,page("?username=audit-test&from=2026-01-01T00:00:00Z").get("items").size());
    assertEquals(0,page("?username=audit-test&storage=ARCHIVED").get("items").size());
    var result=body(mvc.perform(post("/api/authorization-logs/archive").header("Authorization",admin).contentType("application/json").content("{\"before\":\"2026-01-02T00:00:00Z\",\"batchSize\":2}")).andExpect(status().isOk())).get("data");
    assertEquals(2,result.get("archived").asInt());assertTrue(result.get("hasMore").asBoolean());
    mvc.perform(post("/api/authorization-logs/archive").header("Authorization",admin).contentType("application/json").content("{\"before\":\"2026-01-02T00:00:00Z\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(false));
    assertEquals(4,page("?username=audit-test&storage=ARCHIVED").get("items").size());assertEquals(0,page("?username=audit-test&storage=ACTIVE").get("items").size());
    assertEquals(4,page("?username=audit-test").get("items").size());
    mvc.perform(post("/api/authorization-logs/archive").header("Authorization",admin).contentType("application/json").content("{\"before\":\"2026-01-02T00:00:00Z\"}")).andExpect(jsonPath("$.data.archived").value(0));
    assertEquals(3,db.queryForObject("SELECT count(*) FROM authorization_changes WHERE action='ARCHIVE_LOGS'",Integer.class));
  }
  @Test void invalidQueriesAndArchiveInputsFailBeforeWrites()throws Exception{
    for(String[] param:new String[][]{{"limit","0"},{"limit","501"},{"storage","BAD"},{"userId","0"},{"userId","abc"},{"allowed","bad"},{"from","today"},{"to","today"},{"username","x".repeat(101)},{"appCode","x".repeat(192)},{"event","x".repeat(33)},{"cursor","x".repeat(4097)}})
      mvc.perform(get("/api/authorization-logs").header("Authorization",admin).param(param[0],param[1])).andExpect(status().isBadRequest());
    for(String end:List.of("2026-01-01T00:00:00Z","2025-01-01T00:00:00Z"))mvc.perform(get("/api/authorization-logs").header("Authorization",admin).param("from","2026-01-01T00:00:00Z").param("to",end)).andExpect(status().isBadRequest());
    for(String input:List.of("{}","{\"before\":\"bad\"}","{\"before\":\"2099-01-01T00:00:00Z\"}","{\"before\":\"2026-01-01T00:00:00Z\",\"batchSize\":0}","{\"before\":\"2026-01-01T00:00:00Z\",\"batchSize\":5001}"))mvc.perform(post("/api/authorization-logs/archive").header("Authorization",admin).contentType("application/json").content(input)).andExpect(status().isBadRequest());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM authorization_changes WHERE action='ARCHIVE_LOGS'",Integer.class));
  }
  @Test void loginFailuresAndAnonymousRequestsAreAuditedWithoutCredentials()throws Exception{
    for(String body:List.of("{\"account\":\"admin\",\"password\":\"do-not-record\"}","{\"account\":\"missing-audit-user\",\"password\":\"do-not-record\"}")){
      var result=mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized()).andReturn();
      var row=db.queryForMap("SELECT * FROM authorization_logs WHERE request_id=?",result.getResponse().getHeader("X-Request-Id"));assertEquals("LOGIN_FAILED",row.get("event_type"));assertFalse(row.toString().contains("do-not-record"));assertEquals(401,row.get("http_status"));
    }
    var result=mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
    assertEquals("UNAUTHENTICATED",db.queryForObject("SELECT event_type FROM authorization_logs WHERE request_id=?",String.class,result.getResponse().getHeader("X-Request-Id")));
    // Success and failure events survive the surrounding test/business transaction through REQUIRES_NEW.
    assertTrue(db.queryForObject("SELECT count(*) FROM authorization_logs WHERE event_type='LOGIN_SUCCESS'",Integer.class)>0);
    for(String field:List.of("account","password"))mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("account",field.equals("account")?"x".repeat(101):"admin","password",field.equals("password")?"x".repeat(201):"x")))).andExpect(status().isBadRequest());
  }
}
