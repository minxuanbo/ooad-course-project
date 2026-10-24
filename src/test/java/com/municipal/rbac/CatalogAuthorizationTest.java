package com.municipal.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CatalogAuthorizationTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
  String employee,manager; long uid,role,app,module,feature,permission; String code,appCode;
  @BeforeEach void setup()throws Exception {
    employee=login("SG010000","123456");manager=login("admin","admin123");
    uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
    role=db.queryForObject("SELECT id FROM roles WHERE code='普通员工'",Long.class);
    // Use one specific grant to make the tests independent of user configuration.
    var p=db.queryForMap("SELECT p.id,p.code,a.id appId,a.code appCode,m.id moduleId,f.id featureId FROM "+AuthService.CATALOG+" WHERE ac.code='read' ORDER BY p.id LIMIT 1");
    permission=((Number)p.get("id")).longValue();app=((Number)p.get("appId")).longValue();module=((Number)p.get("moduleId")).longValue();feature=((Number)p.get("featureId")).longValue();code=p.get("code").toString();appCode=p.get("appCode").toString();
    db.update("DELETE FROM user_roles WHERE user_id=?",uid);db.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)",uid,role);
    db.update("DELETE FROM role_permissions WHERE role_id=?",role);db.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",role,permission);
  }
  String login(String account,String password)throws Exception{
    var result=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("account",account,"password",password))))
      .andExpect(status().isOk()).andReturn().getResponse();
    return "Bearer "+json.readTree(result.getContentAsString()).get("token").asText();
  }
  String path(){return "/api/me/applications/"+app;}
  JsonNode getJson(String path)throws Exception{return json.readTree(mvc.perform(get(path).header("Authorization",employee)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
  void authorize(boolean allowed)throws Exception{
    mvc.perform(post("/api/authorize").header("Authorization",employee).contentType("application/json").content(json.writeValueAsString(Map.of("appCode",appCode,"permissionCode",code,"requestId","catalog-test"))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(allowed));
  }
  @Test void onlyAuthorizedApplicationsAreVisibleAndDirectoryDoesNotLeak()throws Exception{
    JsonNode apps=getJson("/api/me/applications");assertEquals(1,apps.size());assertEquals(app,apps.get(0).get("id").asLong());assertEquals(1,apps.get(0).get("permissionCount").asInt());assertFalse(apps.get(0).has("connected"));
    long other=db.queryForObject("SELECT id FROM applications WHERE id<>? ORDER BY id LIMIT 1",Long.class,app);
    for(String suffix:new String[]{"/modules","/modules/"+module+"/features"})
      mvc.perform(get("/api/me/applications/"+other+suffix).header("Authorization",employee)).andExpect(status().isForbidden()).andExpect(jsonPath("$.modules").doesNotExist()).andExpect(jsonPath("$.features").doesNotExist());
    mvc.perform(post("/api/me/applications/"+other+"/enter").header("Authorization",employee)).andExpect(status().isForbidden()).andExpect(jsonPath("$.application").doesNotExist());
  }
  @Test void modulesAndFeaturesContainBooleanButtonStatesAndNoReadLogs()throws Exception{
    int before=db.queryForObject("SELECT count(*) FROM authorization_logs",Integer.class);
    assertTrue(getJson(path()+"/modules").get("modules").size()>0);
    var list=getJson(path()+"/modules/"+module+"/features").get("features");int grants=0,denials=0;
    for(var f:list)for(var op:f.get("operations")){assertTrue(op.get("allowed").isBoolean());if(op.get("allowed").asBoolean())grants++;else denials++;}
    assertEquals(1,grants);assertTrue(denials>0);assertEquals(before,db.queryForObject("SELECT count(*) FROM authorization_logs",Integer.class));
  }
  @Test void correctEntryContractAndWrongModuleRejected()throws Exception{
    mvc.perform(post(path()+"/enter").header("Authorization",employee)).andExpect(status().isOk()).andExpect(jsonPath("$.next").value("modules")).andExpect(jsonPath("$.application.id").value(app)).andExpect(jsonPath("$.error").doesNotExist());
    long wrong=db.queryForObject("SELECT id FROM modules WHERE app_id<>? ORDER BY id LIMIT 1",Long.class,app);
    mvc.perform(get(path()+"/modules/"+wrong+"/features").header("Authorization",employee)).andExpect(status().isNotFound());
    mvc.perform(get("/api/me/applications/999999999/modules").header("Authorization",employee)).andExpect(status().isNotFound());
  }
  @Test void revokeAndRestoreWithoutRelogin()throws Exception{
    authorize(true);db.update("DELETE FROM role_permissions WHERE role_id=?",role);
    assertEquals(0,getJson("/api/me/applications").size());
    mvc.perform(get(path()+"/modules").header("Authorization",employee)).andExpect(status().isForbidden());
    mvc.perform(get(path()+"/modules/"+module+"/features").header("Authorization",employee)).andExpect(status().isForbidden());authorize(false);
    db.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",role,permission);authorize(true);
    assertEquals(1,getJson("/api/me/applications").size());
  }
  @Test void everyCatalogLevelDisablesGrant()throws Exception{
    for(String table:new String[]{"applications","modules","features","permissions","actions","roles"}){
      String key=table.equals("actions")?"code":"id";Object value=switch(table){case "applications"->app;case "modules"->module;case "features"->feature;case "permissions"->permission;case "roles"->role;default->"read";};
      db.update("UPDATE "+table+" SET enabled=0 WHERE "+key+"=?",value);
      assertEquals(0,getJson("/api/me/applications").size(),table);assertEquals(0,getJson("/api/me/permissions").size(),table);authorize(false);
      mvc.perform(get(path()+"/modules").header("Authorization",employee)).andExpect(status().isForbidden());
      db.update("UPDATE "+table+" SET enabled=1 WHERE "+key+"=?",value);authorize(true);
    }
  }
  @Test void multipleRolesUnionCountsOnce()throws Exception{
    long second=db.queryForObject("SELECT id FROM roles WHERE code='公司领导'",Long.class);
    db.update("DELETE FROM role_permissions WHERE role_id=?",second);db.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",second,permission);db.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)",uid,second);
    assertEquals(1,getJson("/api/me/applications").get(0).get("permissionCount").asInt());db.update("DELETE FROM role_permissions WHERE role_id=?",role);authorize(true);
  }
  @Test void logsAreAdminOnlyAndLimitValidated()throws Exception{
    authorize(true);mvc.perform(get("/api/authorization-logs").header("Authorization",employee)).andExpect(status().isForbidden());
    mvc.perform(get("/api/authorization-logs").header("Authorization",manager)).andExpect(status().isOk()).andExpect(jsonPath("$[0].requestId").value("catalog-test"));
    for(String limit:new String[]{"0","-1","501","abc"})mvc.perform(get("/api/authorization-logs").param("limit",limit).header("Authorization",manager)).andExpect(status().isBadRequest());
    db.update("DELETE FROM role_management_permissions WHERE permission_code='audit.read'");mvc.perform(get("/api/authorization-logs").header("Authorization",manager)).andExpect(status().isForbidden());
  }
  @Test void renamedFeatureKeepsPermissionIdentity()throws Exception{
    db.update("UPDATE features SET name='重命名功能',description='统一说明' WHERE id=?",feature);authorize(true);
    boolean found=false;for(var f:getJson(path()+"/modules/"+module+"/features").get("features"))if(f.get("id").asLong()==feature){found=true;assertEquals("重命名功能",f.get("name").asText());assertEquals("统一说明",f.get("description").asText());}
    assertTrue(found);assertEquals(code,db.queryForObject("SELECT code FROM permissions WHERE id=?",String.class,permission));
  }
  @Test void crossAppPermissionDeniedAndUnknownSessionRejected()throws Exception{
    mvc.perform(post("/api/authorize").header("Authorization",employee).contentType("application/json").content(json.writeValueAsString(Map.of("appCode","wrong","permissionCode",code))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
    mvc.perform(get(path()+"/modules")).andExpect(status().isUnauthorized());
    mvc.perform(get(path()+"/modules").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
  }
  @Test void normalizedKeysAndOrganizationConstraints()throws Exception{
    assertEquals(0,db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='users' AND column_name='company') OR (table_name='permissions' AND column_name IN ('app_id','module_name','feature_name','description','action')))",Integer.class));
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO permissions(feature_id,action_code,code) VALUES(?,'read','duplicate-test')",feature));
    var d=db.queryForMap("SELECT id,company_id FROM departments ORDER BY id LIMIT 1");long other=db.queryForObject("SELECT id FROM departments WHERE company_id<>? LIMIT 1",Long.class,d.get("company_id"));
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE departments SET parent_id=? WHERE id=?",other,d.get("id")));
  }
}
