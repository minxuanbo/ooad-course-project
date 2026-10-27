package com.municipal.rbac;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
class ManagementRepository {
  String companyScope(boolean global,String scope){return global?"1=1":"EXISTS(SELECT 1 FROM departments d WHERE d.company_id=c.id AND "+scope+")";}
  private final JdbcTemplate db;
  ManagementRepository(JdbcTemplate db){this.db=db;}

  List<String> managementPermissions(Object... args){return db.queryForList("SELECT permission_code FROM role_management_permissions WHERE role_id=? ORDER BY permission_code",String.class,args);}
  int deleteManagementPermissions(Object... args){return db.update("DELETE FROM role_management_permissions WHERE role_id=?",args);}
  int insertManagementPermission(Object... args){return db.update("INSERT INTO role_management_permissions(role_id,permission_code) VALUES(?,?)",args);}
  List<Map<String,Object>> assignmentRoles(Object... args){return db.queryForList("SELECT r.id,r.code,r.name,r.enabled,(ur.user_id IS NOT NULL) assigned,(SELECT count(*) FROM role_management_permissions mp WHERE mp.role_id=r.id) managementPermissionCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id AND ur.user_id=? ORDER BY r.id",args);}
  List<Map<String,Object>> assignmentScopes(Object... args){return db.queryForList("SELECT role_id roleId,scope_type scope,COALESCE(company_id,(SELECT company_id FROM departments WHERE id=department_id)) companyId,department_id departmentId FROM management_scopes WHERE user_id=? ORDER BY id",args);}
  Integer activeUserCount(Object... args){return db.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,args);}
  int deleteScopes(Object... args){return db.update("DELETE FROM management_scopes WHERE user_id=?",args);}
  int insertScope(Object... args){return db.update("INSERT INTO management_scopes(user_id,role_id,scope_type,company_id,department_id) VALUES(?,?,?,?,?)",args);}
  List<String> activeRoleCode(Object... args){return db.queryForList("SELECT code FROM roles WHERE id=? AND enabled=1",String.class,args);}
  Integer activeCompanyCount(Object... args){return db.queryForObject("SELECT count(*) FROM companies WHERE id=? AND enabled=1",Integer.class,args);}
  Integer activeDepartmentCount(Object... args){return db.queryForObject("SELECT count(*) FROM departments WHERE id=? AND company_id=? AND enabled=1",Integer.class,args);}
  List<Map<String,Object>> findCompanies(String fragment0,Object... args){return db.queryForList("SELECT c.id,c.code,c.name,c.enabled FROM companies c WHERE "+fragment0+" ORDER BY c.id",args);}
  List<Map<String,Object>> findDepartments(String fragment0,String fragment1,Object... args){return db.queryForList("SELECT d.id,d.code,d.name,d.company_id companyId,d.enabled,("+fragment0+") writable FROM departments d WHERE "+fragment1+" ORDER BY d.id",args);}
  int insertCompany(Object... args){return db.update("INSERT INTO companies(code,name) VALUES(?,?)",args);}
  Long companyId(Object... args){return db.queryForObject("SELECT id FROM companies WHERE code=?",Long.class,args);}
  int updateCompany(Object... args){return db.update("UPDATE companies SET code=COALESCE(?,code),name=COALESCE(?,name) WHERE id=?",args);}
  Integer companyCount(Object... args){return db.queryForObject("SELECT count(*) FROM companies WHERE id=?",Integer.class,args);}
  int insertDepartment(Object... args){return db.update("INSERT INTO departments(company_id,code,name) VALUES(?,?,?)",args);}
  Long departmentId(Object... args){return db.queryForObject("SELECT id FROM departments WHERE code=?",Long.class,args);}
  int updateDepartment(Object... args){return db.update("UPDATE departments SET code=COALESCE(?,code),name=COALESCE(?,name) WHERE id=?",args);}
  int recordChange(Object... args){return db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",args);}
}
