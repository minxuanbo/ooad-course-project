package com.municipal.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:sqlite::memory:","spring.datasource.hikari.maximum-pool-size=1"})
@AutoConfigureMockMvc
@Transactional
class AccessBoundaryTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  String login(String account,String password)throws Exception{
    String response=mvc.perform(post("/api/auth/login").contentType("application/json")
      .content(json.writeValueAsString(java.util.Map.of("account",account,"password",password))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return "Bearer "+json.readTree(response).get("token").asText();
  }
  @Test void employeeCannotReadOrWriteManagementEndpoints()throws Exception{
    String token=login("SG010000","123456");
    for(String path:new String[]{"/dashboard","/users","/users/1/roles","/roles","/roles/1/permissions","/applications","/authorization-logs"})
      mvc.perform(get("/api"+path).header("Authorization",token)).andExpect(status().isForbidden());
    for(String path:new String[]{"/users/1/roles","/roles/1/permissions"})
      mvc.perform(put("/api"+path).header("Authorization",token).contentType("application/json").content("{\"ids\":[]}")).andExpect(status().isForbidden());
    mvc.perform(get("/api/me").header("Authorization",token)).andExpect(jsonPath("$.canManage").value(false));
  }
  @Test void handoverPreservesAccessThenRevokesSessionsAndKeepsHistory()throws Exception{
    String employee=login("SG010000","123456"),manager=login("admin","admin123");
    long uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
    String path="/api/users/"+uid+"/handover";
    String start="{\"receiverNo\":\"SG000012\",\"ownerNo\":\"SG000011\",\"expectedDate\":\"2026-10-10\",\"reason\":\"测试交接\"}";
    mvc.perform(post(path).header("Authorization",employee).contentType("application/json").content(start)).andExpect(status().isForbidden());
    mvc.perform(post(path).header("Authorization",manager).contentType("application/json").content(start)).andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization",employee)).andExpect(status().isOk());
    assertTrue(db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=?",Integer.class,uid)>0);
    mvc.perform(post(path).header("Authorization",manager).contentType("application/json").content(start)).andExpect(status().isBadRequest());
    mvc.perform(post(path+"/complete").header("Authorization",manager).contentType("application/json").content("{\"note\":\"完成\"}")).andExpect(status().isBadRequest());
    assertEquals("ACTIVE",db.queryForObject("SELECT status FROM users WHERE id=?",String.class,uid));
    String finish="{\"note\":\"交接资料已核验\",\"confirmed\":\"true\"}";
    mvc.perform(post(path+"/complete").header("Authorization",manager).contentType("application/json").content(finish)).andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization",employee)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"account\":\"SG010000\",\"password\":\"123456\"}")).andExpect(status().isUnauthorized());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=?",Integer.class,uid));
    assertEquals("DEPARTED",db.queryForObject("SELECT state FROM handovers WHERE user_id=?",String.class,uid));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,uid));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=? AND revoked=0",Integer.class,uid));
    mvc.perform(post(path+"/complete").header("Authorization",manager).contentType("application/json").content(finish)).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM authorization_changes WHERE target_id=? AND action='COMPLETE_HANDOVER'",Integer.class,uid));
    mvc.perform(put("/api/users/"+uid+"/roles").header("Authorization",manager).contentType("application/json").content("{\"ids\":[5]}")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/users/"+uid+"/handover").header("Authorization",manager)).andExpect(jsonPath("$.handover.state").value("DEPARTED"));
    assertEquals("ACTIVE",db.queryForObject("SELECT status FROM users WHERE employee_no='SG000012'",String.class));
  }
  @Test void handoverRejectsInvalidPeopleDatesAndSelf()throws Exception{
    String manager=login("admin","admin123");
    long uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
    String path="/api/users/"+uid+"/handover";
    String body="{\"receiverNo\":\"SG010000\",\"ownerNo\":\"SG000011\",\"expectedDate\":\"2026-10-10\",\"reason\":\"交接\"}";
    mvc.perform(post(path).header("Authorization",manager).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    mvc.perform(post("/api/users/1/handover").header("Authorization",manager).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    mvc.perform(post(path).header("Authorization",manager).contentType("application/json").content(body.replace("2026-10-10","invalid"))).andExpect(status().isBadRequest());
    mvc.perform(post(path+"/complete").header("Authorization",manager).contentType("application/json").content("{\"note\":\"完成\",\"confirmed\":\"true\"}")).andExpect(status().isBadRequest());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM handovers",Integer.class));
  }
  @Test void sameSessionSeesRevokeAndRegrantImmediately()throws Exception{
    String employee=login("SG010000","123456"), manager=login("admin","admin123");
    long uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
    long role=db.queryForObject("SELECT id FROM roles WHERE name='普通员工'",Long.class);
    String response=mvc.perform(get("/api/me/applications").header("Authorization",employee)).andReturn().getResponse().getContentAsString();
    long app=0;
    for(JsonNode a:json.readTree(response))if(a.get("permissionCount").asInt()>0){app=a.get("id").asLong();break;}
    assertTrue(app>0);
    mvc.perform(post("/api/me/applications/"+app+"/enter").header("Authorization",employee)).andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false));
    mvc.perform(put("/api/users/"+uid+"/roles").header("Authorization",manager).contentType("application/json").content("{\"ids\":[]}")).andExpect(status().isOk());
    mvc.perform(post("/api/me/applications/"+app+"/enter").header("Authorization",employee)).andExpect(status().isForbidden());
    response=mvc.perform(get("/api/me/applications").header("Authorization",employee)).andReturn().getResponse().getContentAsString();
    for(JsonNode a:json.readTree(response))assertEquals(0,a.get("permissionCount").asInt());
    mvc.perform(put("/api/users/"+uid+"/roles").header("Authorization",manager).contentType("application/json").content("{\"ids\":["+role+"]}")).andExpect(status().isOk());
    mvc.perform(post("/api/me/applications/"+app+"/enter").header("Authorization",employee)).andExpect(status().isOk());
    db.update("UPDATE roles SET enabled=0 WHERE id=?",role);
    mvc.perform(post("/api/me/applications/"+app+"/enter").header("Authorization",employee)).andExpect(status().isForbidden());
    assertTrue(db.queryForObject("SELECT count(*) FROM authorization_logs WHERE user_id=? AND allowed=0",Integer.class,uid)>=2);
  }
  @Test void rolePermissionAndAdminRevocationApplyToExistingSessions()throws Exception{
    String employee=login("SG010000","123456"), manager=login("admin","admin123");
    db.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE name='普通员工')");
    String response=mvc.perform(get("/api/me/applications").header("Authorization",employee)).andReturn().getResponse().getContentAsString();
    for(JsonNode a:json.readTree(response))assertEquals(0,a.get("permissionCount").asInt());
    db.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE username='admin')");
    mvc.perform(get("/api/me").header("Authorization",manager)).andExpect(jsonPath("$.canManage").value(false));
    mvc.perform(get("/api/users").header("Authorization",manager)).andExpect(status().isForbidden());
  }
}
