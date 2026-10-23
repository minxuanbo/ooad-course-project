package com.municipal.rbac;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class EndpointBoundaryTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
  String admin; long uid;
  @BeforeEach void setup() throws Exception {
    admin=login("admin","admin123");
    uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
  }
  String login(String account,String password)throws Exception{
    var r=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("account",account,"password",password))))
      .andExpect(status().isOk()).andReturn().getResponse();
    return "Bearer "+json.readTree(r.getContentAsString()).get("token").asText();
  }
  @Test void invalidLoginAndExpiredOrRevokedSessions()throws Exception{
    for(String body:List.of("{}","{\"account\":\"admin\"}","{"))
      mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isBadRequest());
    for(String account:List.of("admin","no-such-person"))
      mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("account",account,"password","wrong")))).andExpect(status().isUnauthorized());
    String token=login("SG010000","123456");
    db.update("UPDATE sessions SET expires_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE user_id=?",uid);
    mvc.perform(get("/api/me").header("Authorization",token)).andExpect(status().isUnauthorized());
    token=login("SG010000","123456");
    mvc.perform(post("/api/auth/logout").header("Authorization",token)).andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization",token)).andExpect(status().isUnauthorized());
    for(String header:List.of("invalid","Bearer invalid")){
      mvc.perform(get("/api/me").header("Authorization",header)).andExpect(status().isUnauthorized());
      mvc.perform(post("/api/auth/logout").header("Authorization",header)).andExpect(status().isOk());
    }
    mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
  }
  @Test void authorizationNullAndLengthBoundaries()throws Exception{
    for(String body:List.of("{}","{\"permissionCode\":\"missing\"}","{\"appCode\":\"missing\"}"))
      mvc.perform(post("/api/authorize").header("Authorization",admin).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
    for(var entry:Map.of("appCode",192,"permissionCode",513,"requestId",192).entrySet())
      mvc.perform(post("/api/authorize").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(Map.of(entry.getKey(),"x".repeat(entry.getValue()))))).andExpect(status().isBadRequest());
  }
  @Test void roleReplacementDeduplicatesAndIgnoresUnknownIds()throws Exception{
    long role=db.queryForObject("SELECT id FROM roles WHERE code='普通员工'",Long.class);
    long permission=db.queryForObject("SELECT MIN(id) FROM permissions",Long.class);
    for(String path:List.of("/users/"+uid+"/roles","/roles/"+role+"/permissions"))
      mvc.perform(put("/api"+path).header("Authorization",admin).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    mvc.perform(put("/api/users/0/roles").header("Authorization",admin).contentType("application/json").content("{\"ids\":[]}")).andExpect(status().isNotFound());
    mvc.perform(put("/api/users/"+uid+"/roles").header("Authorization",admin).contentType("application/json").content("{\"ids\":["+role+","+role+",0]}")).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=?",Integer.class,uid));
    mvc.perform(get("/api/users/"+uid+"/roles").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
    mvc.perform(post("/api/me/applications/0/enter").header("Authorization",admin)).andExpect(status().isNotFound());
    mvc.perform(put("/api/roles/"+role+"/permissions").header("Authorization",admin).contentType("application/json").content("{\"ids\":["+permission+","+permission+",0]}")).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM role_permissions WHERE role_id=?",Integer.class,role));
    mvc.perform(put("/api/roles/"+role+"/permissions").header("Authorization",admin).contentType("application/json").content("{\"ids\":[]}")).andExpect(status().isOk());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM role_permissions WHERE role_id=?",Integer.class,role));
  }
  @Test void handoverValidationAndNoPartialWrite()throws Exception{
    var valid=new HashMap<String,String>(Map.of("receiverNo","SG000012","ownerNo","SG000011","expectedDate","2026-10-31","reason","Test only"));
    var invalid=new ArrayList<Map<String,String>>();
    for(String field:List.of("reason","expectedDate","receiverNo","ownerNo")){
      var missing=new HashMap<>(valid); missing.remove(field);invalid.add(missing);
      var blank=new HashMap<>(valid);blank.put(field," ");invalid.add(blank);
      var longValue=new HashMap<>(valid);longValue.put(field,"x".repeat(1001));invalid.add(longValue);
    }
    for(var e:Map.of("receiverNo","MISSING","ownerNo","SG010000","expectedDate","bad-date","oaReference","x".repeat(201)).entrySet()){
      var b=new HashMap<>(valid);b.put(e.getKey(),e.getValue());invalid.add(b);
    }
    for(var body:invalid)
      mvc.perform(post("/api/users/"+uid+"/handover").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM handovers WHERE user_id=?",Integer.class,uid));
    mvc.perform(post("/api/users/0/handover").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(valid))).andExpect(status().isNotFound());
    mvc.perform(get("/api/users/0/handover").header("Authorization",admin)).andExpect(status().isNotFound());
    mvc.perform(get("/api/users/"+uid+"/handover").header("Authorization",admin)).andExpect(jsonPath("$.handover").isEmpty());
    mvc.perform(post("/api/users/1/handover/complete").header("Authorization",admin).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    valid.put("oaReference",null);
    mvc.perform(post("/api/users/"+uid+"/handover").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(valid))).andExpect(status().isOk());
    assertEquals("",db.queryForObject("SELECT oa_reference FROM handovers WHERE user_id=?",String.class,uid));
    mvc.perform(get("/api/users/"+uid+"/handover").header("Authorization",admin)).andExpect(jsonPath("$.handover.state").value("HANDOVER"));
    db.update("UPDATE users SET status='DISABLED' WHERE id=?",uid);
    mvc.perform(post("/api/users/"+uid+"/handover").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(valid))).andExpect(status().isBadRequest());
  }
  @Test void currentUnknownRouteAndMethodLimitationIsExplicit()throws Exception{
    // Characterize the current generic exception mapping; do not claim 404/405 are implemented.
    mvc.perform(get("/api/no-such-route")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("服务器内部错误"));
    mvc.perform(get("/api/auth/login")).andExpect(status().isInternalServerError());
  }
}
