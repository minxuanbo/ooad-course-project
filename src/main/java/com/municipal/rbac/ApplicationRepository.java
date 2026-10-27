package com.municipal.rbac;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
class ApplicationRepository {
  private final JdbcTemplate db;
  ApplicationRepository(JdbcTemplate db){this.db=db;}
  String userWhere(String q,String company,Long departmentId,List<Object> args,String scope){
    String like="%"+q.trim()+"%";
    args.add(like);args.add(like);args.add(like);args.add(like);
    String where="(u.name LIKE ? OR u.username LIKE ? OR u.phone LIKE ? OR u.employee_no LIKE ?)";
    if(!company.isEmpty()){where+=" AND c.name=?";args.add(company);}
    if(departmentId!=null){where+=" AND u.department_id=?";args.add(departmentId);}
    return where+" AND "+scope;
  }

  List<Map<String,Object>> myPermissions(long id,String app){
    String sql="SELECT a.code appCode,a.name appName,p.code,m.name module,f.name feature,ac.name action FROM "+AuthRepository.CATALOG+" WHERE "+AuthRepository.ACTIVE+" AND "+AuthRepository.GRANTED;
    return app==null?rows(sql+" ORDER BY appCode,p.code",id):rows(sql+" AND a.code=? ORDER BY p.code",id,app);
  }
  int scalar(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
  private List<Map<String,Object>> rows(String sql,Object...args){return db.query(sql,(rs,n)->{var m=new LinkedHashMap<String,Object>();var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++){Object value=rs.getObject(i);if(value instanceof java.sql.Timestamp time)value=time.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));else if(value instanceof java.time.LocalDateTime time)value=time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));else if(value instanceof java.sql.Date date)value=date.toLocalDate().toString();m.put(md.getColumnLabel(i),value);}return m;},args);}

  List<Map<String,Object>> rolesOfUser(Object... args){return rows("SELECT r.id,r.code,r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.id",args);}
  Integer permissionCount(Object... args){return db.queryForObject("SELECT count(DISTINCT p.id) FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=?",Integer.class,args);}
  Integer permissionInApplication(Object... args){return db.queryForObject("SELECT count(*) FROM "+AuthRepository.CATALOG+" WHERE p.code=? AND a.code=?",Integer.class,args);}
  int recordDecision(Object... args){return db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,request_id,reason) VALUES(?,?,?,?,?,?,?)",args);}
  Integer countScopedUsers(String fragment0,Object... args){return db.queryForObject("SELECT count(*) FROM users u WHERE "+fragment0,Integer.class,args);}
  Integer countScopedDepartments(String fragment0,Object... args){return db.queryForObject("SELECT count(*) FROM departments d WHERE "+fragment0,Integer.class,args);}
  Long countUsers(String fragment0,Object... args){return db.queryForObject("SELECT count(*) FROM users u LEFT JOIN departments d ON d.id=u.department_id LEFT JOIN companies c ON c.id=d.company_id WHERE "+fragment0,Long.class,args);}
  List<Map<String,Object>> companyOptions(String fragment0,Object... args){return rows("SELECT DISTINCT c.name company FROM companies c JOIN departments d ON d.company_id=c.id JOIN users u ON u.department_id=d.id WHERE "+fragment0+" ORDER BY c.name",args);}
  List<Map<String,Object>> departmentOptions(String fragment0,Object... args){return rows("SELECT DISTINCT d.id,d.name FROM departments d JOIN companies c ON c.id=d.company_id JOIN users u ON u.department_id=d.id WHERE (?='' OR c.name=?) AND "+fragment0+" ORDER BY d.name,d.id",args);}
  List<Map<String,Object>> findUsers(String fragment0,Object... args){return rows("SELECT u.id,u.employee_no employeeNo,u.username,u.name,u.phone,c.name company,u.department_id departmentId,d.name department,u.position,u.status,"+"COALESCE((SELECT state FROM handovers WHERE user_id=u.id),'EMPLOYED') employmentStatus,(SELECT GROUP_CONCAT(r.name ORDER BY r.id SEPARATOR '、') FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id) roles "+"FROM users u LEFT JOIN departments d ON d.id=u.department_id LEFT JOIN companies c ON c.id=d.company_id "+"WHERE "+fragment0+" ORDER BY u.id LIMIT ? OFFSET ?",args);}
  List<Map<String,Object>> roleOptions(Object... args){return rows("SELECT r.id,r.code,r.name,CASE WHEN ur.user_id IS NULL THEN 0 ELSE 1 END assigned FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id AND ur.user_id=? ORDER BY r.id",args);}
  List<Map<String,Object>> lockUser(Object... args){return rows("SELECT id FROM users WHERE id=? FOR UPDATE",args);}
  Integer departedCount(Object... args){return db.queryForObject("SELECT count(*) FROM handovers WHERE user_id=? AND state=\'DEPARTED\'",Integer.class,args);}
  int recordChange(Object... args){return db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",args);}
  List<Map<String,Object>> findRoles(String fragment0,Object... args){return rows("SELECT r.id,r.code,r.name,r.description,r.enabled,count(DISTINCT CASE WHEN "+fragment0+" THEN ur.user_id END) userCount,count(DISTINCT rp.permission_id) permissionCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id LEFT JOIN users ru ON ru.id=ur.user_id LEFT JOIN role_permissions rp ON rp.role_id=r.id GROUP BY r.id ORDER BY r.id",args);}
  List<Map<String,Object>> permissionOptions(Object... args){return rows("SELECT p.id,a.name appName,m.name module,f.name feature,ac.name action,p.code,CASE WHEN rp.role_id IS NULL THEN 0 ELSE 1 END assigned FROM "+AuthRepository.CATALOG+" LEFT JOIN role_permissions rp ON rp.permission_id=p.id AND rp.role_id=? WHERE f.name LIKE ? OR m.name LIKE ? OR a.name LIKE ? ORDER BY a.id,p.id",args);}
  int deleteRolePermissions(Object... args){return db.update("DELETE FROM role_permissions WHERE role_id=?",args);}
  int insertRolePermission(Object... args){return db.update("INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions WHERE id=?",args);}
  int recordRoleChange(Object... args){return db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",args);}
  List<Map<String,Object>> findApplications(Object... args){return rows("SELECT a.id,a.code,a.name,a.enabled,count(p.id) permissionCount FROM applications a LEFT JOIN modules m ON m.app_id=a.id LEFT JOIN features f ON f.module_id=m.id LEFT JOIN permissions p ON p.feature_id=f.id GROUP BY a.id ORDER BY a.id",args);}
  List<Map<String,Object>> accessibleApplications(Object... args){return rows("SELECT a.id,a.code,a.name,count(DISTINCT p.id) permissionCount FROM "+AuthRepository.CATALOG+" WHERE "+AuthRepository.ACTIVE+" AND "+AuthRepository.GRANTED+" GROUP BY a.id ORDER BY a.id",args);}
  List<Map<String,Object>> findApplication(Object... args){return rows("SELECT id,code,name FROM applications WHERE id=?",args);}
  int recordApplicationEntry(Object... args){return db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,reason,event_type,request_id,http_method,request_path,http_status) VALUES(?,?,?,?,?,?,'APPLICATION_ENTRY',?,'POST',?,?)",args);}
  List<Map<String,Object>> findModules(Object... args){return rows("SELECT m.id,m.name,(SELECT count(*) FROM features f WHERE f.module_id=m.id AND f.enabled=1) featureCount,"+"(SELECT count(*) FROM permissions p JOIN features f ON f.id=p.feature_id JOIN actions ac ON ac.code=p.action_code WHERE f.module_id=m.id AND f.enabled=1 AND p.enabled=1 AND ac.enabled=1) permissionCount,"+"(SELECT count(*) FROM permissions p JOIN features f ON f.id=p.feature_id JOIN actions ac ON ac.code=p.action_code WHERE f.module_id=m.id AND f.enabled=1 AND p.enabled=1 AND ac.enabled=1 AND "+AuthRepository.GRANTED+") grantedPermissionCount "+"FROM modules m WHERE m.app_id=? AND m.enabled=1 ORDER BY m.sort_order,m.id",args);}
  Integer moduleInApplication(Object... args){return db.queryForObject("SELECT count(*) FROM modules WHERE id=? AND app_id=? AND enabled=1",Integer.class,args);}
  List<Map<String,Object>> findFeatures(Object... args){return rows("SELECT id,name,description FROM features WHERE module_id=? AND enabled=1 ORDER BY sort_order,id",args);}
  List<Map<String,Object>> findOperations(Object... args){return rows("SELECT p.feature_id featureId,p.id permissionId,p.code permissionCode,ac.code actionCode,ac.name label,("+AuthRepository.GRANTED+") allowed FROM permissions p JOIN features f ON f.id=p.feature_id JOIN actions ac ON ac.code=p.action_code WHERE f.module_id=? AND f.enabled=1 AND p.enabled=1 AND ac.enabled=1 ORDER BY ac.sort_order,ac.code,p.id",args);}
  List<Map<String,Object>> userIdentity(Object... args){return rows("SELECT id,name,username,status FROM users WHERE id=?",args);}
  List<Map<String,Object>> handoverDetail(Object... args){return rows("SELECT h.*,receiver.name receiver_name,receiver.employee_no receiver_no,owner.name owner_name,owner.employee_no owner_no "+"FROM handovers h JOIN users receiver ON receiver.id=h.receiver_id JOIN users owner ON owner.id=h.owner_id WHERE h.user_id=?",args);}
  List<Map<String,Object>> handoverRoles(Object... args){return rows("SELECT r.id,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?",args);}
  List<Map<String,Object>> handoverSystems(Object... args){return rows("SELECT DISTINCT a.name FROM "+AuthRepository.CATALOG+" WHERE "+AuthRepository.ACTIVE+" AND "+AuthRepository.GRANTED,args);}
  List<Map<String,Object>> handoverHistory(Object... args){return rows("SELECT occurred_at,operator_id,action,detail FROM authorization_changes WHERE target_type='USER' AND target_id=? AND (action NOT IN ('SET_MANAGEMENT_GRANTS','SET_ROLE_ASSIGNMENTS') OR ?) ORDER BY id DESC LIMIT 50",args);}
  List<Map<String,Object>> lockUserStatus(Object... args){return rows("SELECT id,status FROM users WHERE id=? FOR UPDATE",args);}
  Integer handoverCount(Object... args){return db.queryForObject("SELECT count(*) FROM handovers WHERE user_id=?",Integer.class,args);}
  int insertHandover(Object... args){return db.update("INSERT INTO handovers(user_id,receiver_id,owner_id,expected_date,reason,oa_reference,state,started_by) VALUES(?,?,?,?,?,?,'HANDOVER',?)",args);}
  List<Map<String,Object>> lockDepartingUser(Object... args){return rows("SELECT id FROM users WHERE id=? FOR UPDATE",args);}
  List<Map<String,Object>> lockHandover(Object... args){return rows("SELECT state FROM handovers WHERE user_id=? FOR UPDATE",args);}
  List<Map<String,Object>> rolesBeforeDeparture(Object... args){return rows("SELECT r.id,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?",args);}
  int removeUserRoles(Object... args){return db.update("DELETE FROM user_roles WHERE user_id=?",args);}
  int disableUser(Object... args){return db.update("UPDATE users SET status='DISABLED' WHERE id=?",args);}
  int revokeSessions(Object... args){return db.update("UPDATE sessions SET revoked=1 WHERE user_id=?",args);}
  int finishHandover(Object... args){return db.update("UPDATE handovers SET state='DEPARTED',completed_by=?,completed_at=CURRENT_TIMESTAMP,completion_note=? WHERE user_id=?",args);}
  List<Long> existingRoleIds(Object... args){return db.queryForList("SELECT role_id FROM user_roles WHERE user_id=?",Long.class,args);}
  Integer permissionsOutsideActor(Object... args){return db.queryForObject("SELECT count(*) FROM role_permissions rp WHERE rp.role_id=? AND NOT EXISTS(SELECT 1 FROM "+AuthRepository.CATALOG+" WHERE p.id=rp.permission_id AND "+AuthRepository.ACTIVE+" AND "+AuthRepository.GRANTED+")",Integer.class,args);}
  List<Map<String,Object>> eligibleHandoverPerson(Object... args){return rows("SELECT u.id FROM users u LEFT JOIN handovers h ON h.user_id=u.id WHERE u.employee_no=? AND u.status='ACTIVE' AND h.user_id IS NULL",args);}
  int recordUserChange(Object... args){return db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,'USER',?,?,?)",args);}
}
