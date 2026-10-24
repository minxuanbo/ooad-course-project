package com.municipal.rbac;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class ManagementController {
  private final JdbcTemplate db;
  private final AuthService auth;
  private final ManagementAccess access;
  ManagementController(JdbcTemplate db,AuthService auth){this.db=db;this.auth=auth;this.access=auth.management();}
  record Scope(Long roleId,String scope,Long companyId,Long departmentId){}
  record Assignments(List<Long> roleIds,List<Scope> scopes){}
  record Permissions(List<String> permissions){}
  record Organization(String code,String name,Long companyId,Long parentId){}
  private long operator(String header){return (long)auth.require(header).get("id");}
  @GetMapping("/management/permissions") Map<String,String> catalog(@RequestHeader("Authorization")String h){
    access.requireGlobal(operator(h),"role.permissions");return ManagementAccess.PERMISSIONS;
  }
  @GetMapping("/roles/{id}/management-permissions") List<String> rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id){
    access.require(operator(h),"role.read");return db.queryForList("SELECT permission_code FROM role_management_permissions WHERE role_id=? ORDER BY permission_code",String.class,id);
  }
  @PutMapping("/roles/{id}/management-permissions") @Transactional
  void rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Permissions body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"role.permissions");
    String code=roleCode(id);
    if(body.permissions()==null || !ManagementAccess.PERMISSIONS.keySet().containsAll(body.permissions()))throw ApiException.bad("管理权限列表无效");
    if(!code.equals("系统管理员") && body.permissions().stream().anyMatch(ManagementAccess.PLATFORM_ONLY::contains))throw ApiException.bad("日志查看与平台授权配置仅限系统管理员角色");
    db.update("DELETE FROM role_management_permissions WHERE role_id=?",id);
    for(String permission:new LinkedHashSet<>(body.permissions()))db.update("INSERT INTO role_management_permissions(role_id,permission_code) VALUES(?,?)",id,permission);
    access.retainPlatformAdmin();audit(actor,"ROLE",id,"SET_ROLE_MANAGEMENT_PERMISSIONS",body.permissions().toString());
  }
  @GetMapping("/users/{id}/role-assignments") Map<String,Object> assignments(@RequestHeader("Authorization")String h,@PathVariable long id){
    access.requireGlobal(operator(h),"management.grant");
    return Map.of("roles",db.queryForList("SELECT r.id,r.code,r.name,r.enabled,(ur.user_id IS NOT NULL) assigned,(SELECT count(*) FROM role_management_permissions mp WHERE mp.role_id=r.id) managementPermissionCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id AND ur.user_id=? ORDER BY r.id",id),
      "scopes",db.queryForList("SELECT role_id roleId,scope_type scope,company_id companyId,department_id departmentId FROM user_role_scopes WHERE user_id=? ORDER BY id",id));
  }
  @PutMapping("/users/{id}/role-assignments") @Transactional
  void assign(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Assignments body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"management.grant");
    if(body.roleIds()==null || body.scopes()==null || body.scopes().size()>200)throw ApiException.bad("角色与范围列表必填，范围最多 200 条");
    if(db.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,id)==0)throw ApiException.bad("只能为启用用户分配角色与范围");
    for(Long role:body.roleIds())roleCode(role);
    for(Scope scope:body.scopes())validate(scope,body.roleIds());
    access.replaceRoles(id,body.roleIds(),actor);
    db.update("DELETE FROM user_role_scopes WHERE user_id=?",id);
    for(Scope g:new LinkedHashSet<>(body.scopes()))db.update("INSERT INTO user_role_scopes(user_id,role_id,scope_type,company_id,department_id) VALUES(?,?,?,?,?)",id,g.roleId(),g.scope(),g.companyId(),g.departmentId());
    access.retainPlatformAdmin();audit(actor,"USER",id,"SET_ROLE_ASSIGNMENTS",body.toString());
  }
  private String roleCode(Long id){
    var roles=db.queryForList("SELECT code FROM roles WHERE id=? AND enabled=1",String.class,id);
    if(roles.isEmpty())throw ApiException.bad("角色不存在或已停用");return roles.get(0);
  }
  private void validate(Scope g,List<Long> roles){
    if(g==null || g.roleId()==null || !roles.contains(g.roleId()))throw ApiException.bad("范围必须绑定已选择的角色");
    String code=roleCode(g.roleId());
    if(!Set.of("GLOBAL","COMPANY","DEPARTMENT").contains(Objects.toString(g.scope(),"")))throw ApiException.bad("未知管理范围");
    if(code.equals("系统管理员") != g.scope().equals("GLOBAL"))throw ApiException.bad("系统管理员使用全平台范围，其他角色使用公司或部门范围");
    if(g.scope().equals("GLOBAL")){
      if(g.companyId()!=null || g.departmentId()!=null)throw ApiException.bad("平台范围不能指定组织");
    }else{
      if(g.companyId()==null || db.queryForObject("SELECT count(*) FROM companies WHERE id=? AND enabled=1",Integer.class,g.companyId())==0)throw ApiException.bad("公司不存在或已停用");
      if(g.scope().equals("COMPANY")){
        if(g.departmentId()!=null)throw ApiException.bad("公司范围不能指定部门");
      }else if(g.departmentId()==null || db.queryForObject("SELECT count(*) FROM departments WHERE id=? AND company_id=? AND enabled=1",Integer.class,g.departmentId(),g.companyId())==0)throw ApiException.bad("部门不属于指定公司或已停用");
    }
  }
  @GetMapping("/organizations") Map<String,Object> organizations(@RequestHeader("Authorization")String h){
    long actor=operator(h);access.require(actor,"org.read");
    String scope=access.scope(actor,"org.read","d.id");
    return Map.of("companies",db.queryForList("SELECT c.id,c.code,c.name,c.enabled FROM companies c WHERE "+(access.global(actor,"org.read")?"1=1":"EXISTS(SELECT 1 FROM departments d WHERE d.company_id=c.id AND "+scope+")")+" ORDER BY c.id"),
      "departments",db.queryForList("SELECT d.id,d.code,d.name,d.company_id companyId,d.parent_id parentId,d.enabled,("+access.scope(actor,"org.write","d.id")+") writable FROM departments d WHERE "+scope+" ORDER BY d.id"));
  }
  @PostMapping("/organizations/companies") @Transactional
  void createCompany(@RequestHeader("Authorization")String h,@RequestBody Organization body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"org.write");validateOrganization(body);
    db.update("INSERT INTO companies(code,name) VALUES(?,?)",body.code(),body.name());
    audit(actor,"COMPANY",db.queryForObject("SELECT id FROM companies WHERE code=?",Long.class,body.code()),"CREATE_COMPANY",body.name());
  }
  @PutMapping("/organizations/companies/{id}") @Transactional
  void renameCompany(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Organization body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"org.write");validateOrganization(body);
    if(db.update("UPDATE companies SET code=?,name=? WHERE id=?",body.code(),body.name(),id)==0)throw ApiException.bad("公司不存在");
    audit(actor,"COMPANY",id,"UPDATE_COMPANY",body.name());
  }
  @PostMapping("/organizations/departments") @Transactional
  void createDepartment(@RequestHeader("Authorization")String h,@RequestBody Organization body){
    access.lock();long actor=operator(h);access.require(actor,"org.write");validateOrganization(body);
    if(body.companyId()==null || db.queryForObject("SELECT count(*) FROM companies WHERE id=?",Integer.class,body.companyId())==0)throw ApiException.bad("公司不存在");
    if(body.parentId()!=null){
      access.department(actor,"org.write",body.parentId());
      if(db.queryForObject("SELECT count(*) FROM departments WHERE id=? AND company_id=?",Integer.class,body.parentId(),body.companyId())==0)throw ApiException.bad("上级部门必须属于同一公司");
    }
    // Only company/global maintainers may create departments: exact department grants cannot expand their scope.
    if(!access.company(actor,"org.write",body.companyId()))throw ApiException.forbidden();
    db.update("INSERT INTO departments(company_id,code,name,parent_id) VALUES(?,?,?,?)",body.companyId(),body.code(),body.name(),body.parentId());
    audit(actor,"DEPARTMENT",db.queryForObject("SELECT id FROM departments WHERE code=?",Long.class,body.code()),"CREATE_DEPARTMENT",body.name());
  }
  @PutMapping("/organizations/departments/{id}") @Transactional
  void renameDepartment(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Organization body){
    access.lock();long actor=operator(h);access.department(actor,"org.write",id);validateOrganization(body);
    if(body.companyId()!=null || body.parentId()!=null)throw ApiException.bad("此接口仅修改名称与编码，不允许移动组织");
    db.update("UPDATE departments SET code=?,name=? WHERE id=?",body.code(),body.name(),id);
    audit(actor,"DEPARTMENT",id,"UPDATE_DEPARTMENT",body.name());
  }
  private void validateOrganization(Organization body){
    if(body.code()==null || body.code().isBlank() || body.code().length()>100 || body.name()==null || body.name().isBlank() || body.name().length()>191)throw ApiException.bad("请填写有效的组织编码与名称");
  }
  private void audit(long actor,String type,long target,String action,String detail){db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",actor,type,target,action,detail);}
}
