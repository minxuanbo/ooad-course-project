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
class ManagementScopeTest {
  @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired AuthService auth;
  String admin,local;long uid,c1,c2,d1,d2,child,other,a,b,nested;
  @BeforeEach void setup()throws Exception{
    admin=login("admin","admin123");local=login("SG010000","123456");
    uid=db.queryForObject("SELECT id FROM users WHERE username='SG010000'",Long.class);
    c1=company("scope-one");c2=company("scope-two");d1=department("scope-root",c1,null);child=department("scope-child",c1,d1);d2=department("scope-peer",c1,null);other=department("scope-other",c2,null);
    a=user("scope-a",d1);b=user("scope-b",other);nested=user("scope-child",child);
    db.update("UPDATE users SET department_id=? WHERE id=?",d1,uid);
  }
  String login(String account,String password)throws Exception{return "Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("account",account,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
  long company(String code){db.update("INSERT INTO companies(code,name) VALUES(?,?)",code,code);return db.queryForObject("SELECT id FROM companies WHERE code=?",Long.class,code);}
  long department(String code,long company,Long parent){db.update("INSERT INTO departments(code,name,company_id,parent_id) VALUES(?,?,?,?)",code,code,company,parent);return db.queryForObject("SELECT id FROM departments WHERE code=?",Long.class,code);}
  long user(String name,long dept){db.update("INSERT INTO users(employee_no,username,name,password_hash,department_id) VALUES(?,?,?,'not-a-login-hash',?)",name,name,name,dept);return db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);}
  long role(String code){return db.queryForObject("SELECT id FROM roles WHERE code=?",Long.class,code);}
  void permissions(String role,String... codes){long id=role(role);db.update("DELETE FROM role_management_permissions WHERE role_id=?",id);for(String code:codes)db.update("INSERT INTO role_management_permissions(role_id,permission_code) VALUES(?,?)",id,code);}
  void bind(long user,String role,String scope,Long company,Long department){long id=role(role);db.update("INSERT IGNORE INTO user_roles(user_id,role_id) VALUES(?,?)",user,id);db.update("INSERT INTO user_role_scopes(user_id,role_id,scope_type,company_id,department_id) VALUES(?,?,?,?,?)",user,id,scope,company,department);}
  Map<String,Object> g(Long role,String scope,Long company,Long department){var x=new LinkedHashMap<String,Object>();x.put("roleId",role);x.put("scope",scope);x.put("companyId",company);x.put("departmentId",department);return x;}
  Map<String,Object> assignment(List<Long> roles,List<?> scopes){return Map.of("roleIds",roles,"scopes",scopes);}
  JsonNode getJson(String path,String token)throws Exception{return json.readTree(mvc.perform(get("/api"+path).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
  ResultActions putJson(String path,String token,Object body)throws Exception{return mvc.perform(put("/api"+path).header("Authorization",token).contentType("application/json").content(json.writeValueAsString(body)));}
  ResultActions postJson(String path,String token,Object body)throws Exception{return mvc.perform(post("/api"+path).header("Authorization",token).contentType("application/json").content(json.writeValueAsString(body)));}
  @Test void rolePermissionAndScopeCannotCrossMultiply()throws Exception{
    permissions("部门经理","user.read","role.read");permissions("项目经理","user.roles");
    bind(uid,"部门经理","COMPANY",c1,null);bind(uid,"项目经理","COMPANY",c2,null);
    assertEquals(3,getJson("/users/page",local).get("total").asInt());
    assertEquals(3,getJson("/dashboard",local).get("users").asInt());
    assertEquals(0,getJson("/dashboard",local).get("applications").asInt());
    assertEquals(0,getJson("/users/page?departmentId="+other,local).get("total").asInt());
    assertEquals(1,getJson("/users/filters",local).get("companies").size());
    assertEquals(2,getJson("/users/filters",local).get("departments").size());
    putJson("/users/"+a+"/roles",local,Map.of("ids",List.of())).andExpect(status().isForbidden());
    putJson("/users/"+b+"/roles",local,Map.of("ids",List.of())).andExpect(status().isOk());
    mvc.perform(get("/api/users/"+b+"/handover").header("Authorization",local)).andExpect(status().isForbidden());
    assertFalse(getJson("/roles",local).isEmpty());
    db.update("UPDATE roles SET enabled=0 WHERE id=?",role("部门经理"));
    mvc.perform(get("/api/users/page").header("Authorization",local)).andExpect(status().isForbidden());
    db.update("UPDATE roles SET enabled=1 WHERE id=?",role("部门经理"));
    db.update("DELETE FROM user_role_scopes WHERE user_id=?",uid);
    assertFalse(getJson("/me",local).get("canManage").asBoolean());
  }
  @Test void exactDepartmentDoesNotIncludeChildAndSameRoleCanManageSeveralScopes()throws Exception{
    permissions("部门经理","user.read");bind(uid,"部门经理","DEPARTMENT",c1,d1);
    assertEquals(2,getJson("/users/page",local).get("total").asInt());
    assertEquals(0,getJson("/dashboard",local).get("roles").asInt());
    bind(uid,"部门经理","DEPARTMENT",c1,child);assertEquals(3,getJson("/users/page",local).get("total").asInt());
    bind(uid,"部门经理","COMPANY",c2,null);assertEquals(4,getJson("/users/page",local).get("total").asInt());
    assertThrows(IllegalArgumentException.class,()->auth.management().scope(uid,"untrusted","u.department_id"));
    assertTrue(getJson("/me",local).get("managementGrants").get(0).has("roleId"));
  }
  @Test void logsAreExclusiveToSystemAdministratorAcrossAllCompanies()throws Exception{
    permissions("部门经理","audit.read");bind(uid,"部门经理","COMPANY",c1,null);
    mvc.perform(get("/api/authorization-logs").header("Authorization",local)).andExpect(status().isForbidden());
    // Even a malformed out-of-band GLOBAL scope on a non-system role cannot enable the platform permission.
    bind(uid,"部门经理","GLOBAL",null,null);
    mvc.perform(get("/api/authorization-logs").header("Authorization",local)).andExpect(status().isForbidden());
    for(Long target:Arrays.asList(a,b,null))db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed) VALUES(?,'scope','test','test',1)",target);
    assertEquals(3,getJson("/authorization-logs",admin).size());
    assertEquals(3,getJson("/dashboard",admin).get("authLogs").asInt());
  }
  @Test void localCannotEscalateSelfOtherManagersOrBusinessPermissions()throws Exception{
    permissions("部门经理","user.roles");bind(uid,"部门经理","COMPANY",c1,null);
    for(long target:new long[]{uid,1})putJson("/users/"+target+"/roles",local,Map.of("ids",List.of())).andExpect(status().isForbidden());
    bind(a,"部门经理","COMPANY",c1,null);
    putJson("/users/"+a+"/roles",local,Map.of("ids",List.of())).andExpect(status().isForbidden());
    db.update("DELETE FROM user_roles WHERE user_id=?",a);
    long ordinary=role("普通员工"),power=role("系统管理员");
    putJson("/users/"+a+"/roles",local,Map.of("ids",List.of(power))).andExpect(status().isForbidden());
    putJson("/users/"+a+"/roles",local,Map.of("ids",List.of(ordinary))).andExpect(status().isOk());
    db.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)",a,power);
    putJson("/users/"+a+"/roles",local,Map.of("ids",List.of())).andExpect(status().isForbidden());
    putJson("/roles/"+ordinary+"/permissions",local,Map.of("ids",List.of())).andExpect(status().isForbidden());
    putJson("/users/"+uid+"/role-assignments",local,assignment(List.of(power),List.of(g(power,"GLOBAL",null,null)))).andExpect(status().isForbidden());
  }
  @Test void roleDefinitionChangesApplyToAllHoldersWithoutChangingScopes()throws Exception{
    long deptRole=role("部门经理");permissions("部门经理","user.read");bind(uid,"部门经理","DEPARTMENT",c1,d1);bind(a,"部门经理","DEPARTMENT",c2,other);
    var before=db.queryForList("SELECT * FROM user_role_scopes WHERE role_id=? ORDER BY id",deptRole);
    String path="/roles/"+deptRole+"/management-permissions";
    assertEquals(11,getJson("/management/permissions",admin).size());assertEquals(1,getJson(path,admin).size());
    for(Object body:List.of(Map.of(),Map.of("permissions",List.of("invalid")),Map.of("permissions",List.of("audit.read")),Map.of("permissions",List.of("management.grant")),Map.of("permissions",List.of("role.permissions"))))putJson(path,admin,body).andExpect(status().isBadRequest());
    putJson("/roles/0/management-permissions",admin,Map.of("permissions",List.of())).andExpect(status().isBadRequest());
    putJson(path,admin,Map.of("permissions",List.of("user.read","org.read","org.read"))).andExpect(status().isOk());
    assertTrue(auth.management().has(uid,"org.read"));assertTrue(auth.management().has(a,"org.read"));assertEquals(before,db.queryForList("SELECT * FROM user_role_scopes WHERE role_id=? ORDER BY id",deptRole));
    putJson(path,admin,Map.of("permissions",List.of())).andExpect(status().isOk());assertFalse(auth.management().has(uid,"user.read"));
    putJson("/roles/"+role("系统管理员")+"/management-permissions",admin,Map.of("permissions",List.of("management.grant","role.permissions","role.read","audit.read"))).andExpect(status().isOk());
  }
  @Test void roleScopeValidationReplacementAndCascade()throws Exception{
    long deptRole=role("部门经理"),system=role("系统管理员");String path="/users/"+uid+"/role-assignments";
    var scopes=List.of(g(deptRole,"COMPANY",c1,null),g(deptRole,"DEPARTMENT",c2,other));
    putJson(path,admin,assignment(List.of(deptRole),scopes)).andExpect(status().isOk());assertEquals(2,getJson(path,admin).get("scopes").size());
    for(var invalid:Arrays.asList(null,g(null,"COMPANY",c1,null),g(system,"GLOBAL",null,null),g(deptRole,null,null,null),g(deptRole,"bad",null,null),g(deptRole,"GLOBAL",null,null),g(deptRole,"COMPANY",null,null),g(deptRole,"COMPANY",-1L,null),g(deptRole,"COMPANY",c1,d1),g(deptRole,"DEPARTMENT",c1,null),g(deptRole,"DEPARTMENT",c1,other)))putJson(path,admin,assignment(List.of(deptRole),Arrays.asList(invalid))).andExpect(status().isBadRequest());
    for(var invalid:List.of(g(system,"COMPANY",c1,null),g(system,"GLOBAL",c1,null),g(system,"GLOBAL",null,d1)))putJson(path,admin,assignment(List.of(system),List.of(invalid))).andExpect(status().isBadRequest());
    for(Object body:List.of(Map.of(),Map.of("roleIds",List.of()),Map.of("scopes",List.of()),assignment(List.of(deptRole),Collections.nCopies(201,scopes.get(0))),assignment(Arrays.asList((Long)null),List.of()),assignment(List.of(-1L),List.of())))putJson(path,admin,body).andExpect(status().isBadRequest());
    putJson("/users/0/role-assignments",admin,assignment(List.of(deptRole),scopes)).andExpect(status().isBadRequest());
    putJson(path,admin,assignment(List.of(deptRole),List.of(scopes.get(0),scopes.get(0)))).andExpect(status().isOk());assertEquals(1,getJson(path,admin).get("scopes").size());
    new ManagementBootstrap(db).run(null);assertEquals(1,getJson(path,admin).get("scopes").size());
    putJson("/users/"+uid+"/roles",admin,Map.of("ids",List.of(deptRole,role("普通员工")))).andExpect(status().isOk());assertEquals(1,getJson(path,admin).get("scopes").size());
    putJson("/users/"+uid+"/roles",admin,Map.of("ids",List.of(role("普通员工")))).andExpect(status().isOk());assertEquals(0,getJson(path,admin).get("scopes").size());
    putJson(path,admin,assignment(List.of(system),List.of(g(system,"GLOBAL",null,null)))).andExpect(status().isOk());assertTrue(auth.management().global(uid,"management.grant"));
    putJson(path,admin,assignment(List.of(),List.of())).andExpect(status().isOk());assertFalse(auth.management().global(uid,"management.grant"));
  }
  @Test void organizationsAreScopedAndCompanyCanCreateDepartments()throws Exception{
    permissions("部门经理","org.read","org.write");bind(uid,"部门经理","DEPARTMENT",c1,d1);
    assertEquals(1,getJson("/organizations",local).get("companies").size());assertEquals(1,getJson("/organizations",local).get("departments").size());
    putJson("/organizations/departments/"+d1,local,Map.of("code","scope-renamed","name","改名")).andExpect(status().isOk());
    putJson("/organizations/departments/"+child,local,Map.of("code","x","name","x")).andExpect(status().isForbidden());
    for(String key:List.of("parentId","companyId"))putJson("/organizations/departments/"+d1,local,Map.of("code","x","name","x",key,c1)).andExpect(status().isBadRequest());
    postJson("/organizations/departments",local,Map.of("code","scope-new","name","新增", "companyId",c1,"parentId",d1)).andExpect(status().isForbidden());
    bind(uid,"部门经理","COMPANY",c1,null);
    postJson("/organizations/departments",local,Map.of("code","scope-new","name","新增", "companyId",c1,"parentId",d1)).andExpect(status().isOk());
    postJson("/organizations/departments",local,Map.of("code","scope-new-root","name","新增根", "companyId",c1)).andExpect(status().isOk());
    postJson("/organizations/departments",local,Map.of("code","x","name","x", "companyId",c2,"parentId",other)).andExpect(status().isForbidden());
    postJson("/organizations/companies",local,Map.of("code","x","name","x")).andExpect(status().isForbidden());
    putJson("/organizations/companies/"+c1,local,Map.of("code","x","name","x")).andExpect(status().isForbidden());
    assertTrue(getJson("/organizations",admin).get("companies").size()>1);
    postJson("/organizations/companies",admin,Map.of("code","scope-new-co","name","新公司")).andExpect(status().isOk());
    putJson("/organizations/companies/"+c2,admin,Map.of("code","scope-updated","name","改名公司")).andExpect(status().isOk());
    putJson("/organizations/companies/0",admin,Map.of("code","missing","name","missing")).andExpect(status().isBadRequest());
    postJson("/organizations/departments",admin,Map.of("code","x","name","x", "companyId",c2,"parentId",d1)).andExpect(status().isBadRequest());
    postJson("/organizations/departments",admin,Map.of("code","x","name","x")).andExpect(status().isBadRequest());
    postJson("/organizations/departments",admin,Map.of("code","x","name","x","companyId",-1)).andExpect(status().isBadRequest());
    postJson("/organizations/departments",admin,Map.of("code","scope-global-dept","name","全局新增","companyId",c2)).andExpect(status().isOk());
    for(var body:List.of(Map.of(),Map.of("code"," "),Map.of("code","a".repeat(101)),Map.of("code","ok"),Map.of("code","ok","name"," "),Map.of("code","ok","name","a".repeat(192))))postJson("/organizations/companies",admin,body).andExpect(status().isBadRequest());
  }
  @Test void scopedHandoverRequiresAllPeopleInScopeAndRevokesRoleScopes()throws Exception{
    permissions("部门经理","user.read","handover.start","handover.complete");bind(uid,"部门经理","COMPANY",c1,null);
    var body=new HashMap<String,Object>(Map.of("receiverNo","scope-b","ownerNo","scope-child","expectedDate","2026-11-01","reason","范围测试"));
    postJson("/users/"+a+"/handover",local,body).andExpect(status().isForbidden());
    body.put("receiverNo","scope-child");postJson("/users/"+a+"/handover",local,body).andExpect(status().isOk());
    assertEquals("scope-child",getJson("/users/"+a+"/handover",local).get("handover").get("receiver_no").asText());
    db.update("UPDATE users SET department_id=? WHERE id=?",other,nested);
    assertTrue(getJson("/users/"+a+"/handover",local).get("handover").get("receiver_no").isNull());
    postJson("/users/"+a+"/handover/complete",local,Map.of("confirmed","true","note","完成")).andExpect(status().isOk());
    bind(b,"部门经理","COMPANY",c2,null);
    body.put("receiverNo","SG010000");body.put("ownerNo","SG010000");postJson("/users/"+b+"/handover",admin,body).andExpect(status().isOk());
    postJson("/users/"+b+"/handover/complete",admin,Map.of("confirmed","true","note","完成")).andExpect(status().isOk());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM user_role_scopes WHERE user_id=?",Integer.class,b));
  }
}
