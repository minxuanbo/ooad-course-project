package com.municipal.rbac;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
class ManagementAccessRepository {
  private final JdbcTemplate db;
  ManagementAccessRepository(JdbcTemplate db){this.db=db;}
  static final String SOURCE="management_scopes g JOIN user_roles ur ON ur.user_id=g.user_id AND ur.role_id=g.role_id JOIN users actor ON actor.id=g.user_id JOIN roles r ON r.id=g.role_id JOIN role_management_permissions mp ON mp.role_id=r.id";
  static final String EFFECTIVE="actor.status='ACTIVE' AND r.enabled=1 AND (mp.permission_code NOT IN ('audit.read','role.permissions','management.grant') OR (r.code='系统管理员' AND g.scope_type='GLOBAL'))";
  String scope(long uid,String permission,String department){
    return "EXISTS(SELECT 1 FROM "+SOURCE+" WHERE g.user_id="+uid+" AND "+EFFECTIVE+
      " AND mp.permission_code='"+permission+"' AND (g.scope_type='GLOBAL' OR (g.scope_type='COMPANY' AND g.company_id=(SELECT sd.company_id FROM departments sd WHERE sd.id="+department+")) OR (g.scope_type='DEPARTMENT' AND g.department_id="+department+")))";
  }

  List<Map<String,Object>> effectiveGrants(Object... args){return db.queryForList("SELECT g.id,g.role_id roleId,r.name roleName,mp.permission_code permission,g.scope_type scope,COALESCE(g.company_id,d.company_id) companyId,g.department_id departmentId,c.name company,d.name department FROM "+SOURCE+" LEFT JOIN departments d ON d.id=g.department_id LEFT JOIN companies c ON c.id=COALESCE(g.company_id,d.company_id) WHERE g.user_id=? AND "+EFFECTIVE+" ORDER BY g.id,mp.permission_code",args);}
  Integer permissionCount(Object... args){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND "+EFFECTIVE,Integer.class,args);}
  Integer globalPermissionCount(Object... args){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND g.scope_type='GLOBAL' AND "+EFFECTIVE,Integer.class,args);}
  Integer companyPermissionCount(Object... args){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE g.user_id=? AND mp.permission_code=? AND g.scope_type='COMPANY' AND g.company_id=? AND "+EFFECTIVE,Integer.class,args);}
  Integer scopedUserCount(String fragment0,Object... args){return db.queryForObject("SELECT count(*) FROM users u WHERE u.id=? AND "+fragment0,Integer.class,args);}
  Integer scopedDepartmentCount(String fragment0,Object... args){return db.queryForObject("SELECT count(*) FROM departments d WHERE d.id=? AND "+fragment0,Integer.class,args);}
  Integer lockAuthorization(Object... args){return db.queryForObject("SELECT version FROM management_migrations WHERE version=1 FOR UPDATE",Integer.class,args);}
  List<Long> roleIds(Object... args){return db.queryForList("SELECT role_id FROM user_roles WHERE user_id=?",Long.class,args);}
  int deleteRole(Object... args){return db.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?",args);}
  int insertRole(Object... args){return db.update("INSERT IGNORE INTO user_roles(user_id,role_id,granted_by) SELECT ?,id,? FROM roles WHERE id=?",args);}
  Integer platformAdminCount(Object... args){return db.queryForObject("SELECT count(*) FROM "+SOURCE+" WHERE mp.permission_code='management.grant' AND g.scope_type='GLOBAL' AND "+EFFECTIVE,Integer.class,args);}
}
