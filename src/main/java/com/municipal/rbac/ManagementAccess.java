package com.municipal.rbac;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Effective management access is a role permission bound to that user-role scope. */
class ManagementAccess {
  static final Map<String,String> PERMISSIONS = Collections.unmodifiableMap(new LinkedHashMap<>(Map.ofEntries(
    Map.entry("user.read","人员查询"), Map.entry("user.roles","用户角色分配"),
    Map.entry("role.read","角色查看"), Map.entry("role.permissions","角色权限配置"),
    Map.entry("org.read","组织查看"), Map.entry("org.write","组织维护"),
    Map.entry("app.read","系统目录查看"), Map.entry("audit.read","审计日志查看"),
    Map.entry("handover.start","离职发起"), Map.entry("handover.complete","离职完成"),
    Map.entry("management.grant","管理授权配置"))));
  static final Set<String> PLATFORM_ONLY=Set.of("audit.read","role.permissions","management.grant");
  static final String SOURCE="user_role_scopes g JOIN user_roles ur ON ur.user_id=g.user_id AND ur.role_id=g.role_id JOIN users actor ON actor.id=g.user_id JOIN roles r ON r.id=g.role_id JOIN role_management_permissions mp ON mp.role_id=r.id";
  static final String EFFECTIVE="actor.status='ACTIVE' AND r.enabled=1 AND (mp.permission_code NOT IN ('audit.read','role.permissions','management.grant') OR (r.code='系统管理员' AND g.scope_type='GLOBAL'))";
  private final JdbcTemplate db;
  ManagementAccess(JdbcTemplate db){this.db=db;}
  List<Map<String,Object>> grants(long uid){
    return db.queryForList("SELECT g.id,g.role_id roleId,r.name roleName,mp.permission_code permission,g.scope_type scope,g.company_id companyId,g.department_id departmentId,c.name company,d.name department FROM "+SOURCE+" LEFT JOIN companies c ON c.id=g.company_id LEFT JOIN departments d ON d.id=g.department_id WHERE g.user_id=? AND "+EFFECTIVE+" ORDER BY g.id,mp.permission_code",uid);
  }
  boolean has(long uid,String permission){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND "+EFFECTIVE,Integer.class,uid,permission)>0;}
  boolean global(long uid,String permission){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND g.scope_type='GLOBAL' AND "+EFFECTIVE,Integer.class,uid,permission)>0;}
  void require(long uid,String permission){if(!has(uid,permission))throw ApiException.forbidden();}
  void requireGlobal(long uid,String permission){if(!global(uid,permission))throw ApiException.forbidden();}
  String scope(long uid,String permission,String department){
    if(!PERMISSIONS.containsKey(permission))throw new IllegalArgumentException("Unknown management permission");
    return "EXISTS(SELECT 1 FROM "+SOURCE+" WHERE g.user_id="+uid+" AND "+EFFECTIVE+
      " AND mp.permission_code='"+permission+"' AND (g.scope_type='GLOBAL' OR (g.scope_type='COMPANY' AND g.company_id=(SELECT sd.company_id FROM departments sd WHERE sd.id="+department+")) OR (g.scope_type='DEPARTMENT' AND g.department_id="+department+")))";
  }
  boolean company(long uid,String permission,long company){return global(uid,permission)||db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND g.scope_type='COMPANY' AND g.company_id=? AND "+EFFECTIVE,Integer.class,uid,permission,company)>0;}
  void user(long uid,String permission,long target){
    require(uid,permission);
    if(global(uid,permission))return;
    if(db.queryForObject("SELECT count(*) FROM users u WHERE u.id=? AND "+scope(uid,permission,"u.department_id"),Integer.class,target)==0)throw ApiException.forbidden();
  }
  void department(long uid,String permission,long target){
    if(db.queryForObject("SELECT count(*) FROM departments d WHERE d.id=? AND "+scope(uid,permission,"d.id"),Integer.class,target)==0)throw ApiException.forbidden();
  }
  void lock(){db.queryForObject("SELECT version FROM management_migrations WHERE version=1 FOR UPDATE",Integer.class);}
  void protectTarget(long actor,String permission,long target){
    user(actor,permission,target);
    if(!global(actor,permission) && (actor==target || !grants(target).isEmpty()))throw ApiException.forbidden();
  }
  void replaceRoles(long uid,Collection<Long> roles,long actor){
    for(Long old:db.queryForList("SELECT role_id FROM user_roles WHERE user_id=?",Long.class,uid))if(!roles.contains(old))db.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?",uid,old);
    for(Long role:new LinkedHashSet<>(roles))db.update("INSERT IGNORE INTO user_roles(user_id,role_id,granted_by) SELECT ?,id,? FROM roles WHERE id=?",uid,actor,role);
  }
  void retainPlatformAdmin(){
    if(db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE mp.permission_code='management.grant' AND g.scope_type='GLOBAL' AND "+EFFECTIVE,Integer.class)==0)
      throw ApiException.bad("必须保留至少一名可配置管理授权的平台管理员");
  }
}
