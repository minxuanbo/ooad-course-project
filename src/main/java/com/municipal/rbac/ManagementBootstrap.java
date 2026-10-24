package com.municipal.rbac;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Version 2 is a one-time role-model migration, after Excel initialization. */
@Component @Order(20)
class ManagementBootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  ManagementBootstrap(JdbcTemplate db){this.db=db;}
  @Transactional public void run(ApplicationArguments args){
    db.update("INSERT IGNORE INTO management_migrations(version,completed) VALUES(1,1)");
    new ManagementAccess(db).lock();
    db.update("INSERT IGNORE INTO management_migrations(version,completed) VALUES(2,0)");
    if(db.queryForObject("SELECT completed FROM management_migrations WHERE version=2",Integer.class)==1)return;
    // Unknown V3 custom assignments need an explicit conversion, never silently widen them.
    if(db.queryForObject("SELECT count(*) FROM management_grants g WHERE g.scope_type<>'GLOBAL' OR NOT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=g.user_id AND r.code='系统管理员')",Integer.class)>0)
      throw new IllegalStateException("存在自定义旧管理授权，请先核对并显式迁移为角色范围");
    var roleIds=new LinkedHashMap<String,Long>();
    db.query("SELECT id,code FROM roles",rs->{roleIds.put(rs.getString(2),rs.getLong(1));});
    int corrected=0;
    var candidates=db.queryForList("SELECT u.id,u.position,d.name department,IF(d.manager_user_id=u.id,1,0) namedManager FROM users u JOIN departments d ON d.id=u.department_id WHERE u.employee_no REGEXP '^SG[0-9]{6}$' AND u.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM authorization_changes a WHERE a.target_type='USER' AND a.target_id=u.id AND a.action IN ('SET_ROLES','SET_ROLE_ASSIGNMENTS'))");
    for(var row:candidates){
      long uid=((Number)row.get("id")).longValue();
      String primary=RolePolicy.primary((String)row.get("position"),(String)row.get("department"),((Number)row.get("namedManager")).intValue()==1);
      var before=new LinkedHashSet<>(db.queryForList("SELECT role_id FROM user_roles WHERE user_id=?",Long.class,uid));
      var after=new LinkedHashSet<>(before);for(String role:List.of("公司领导","部门经理","项目经理"))after.remove(roleIds.get(role));
      after.add(roleIds.get("普通员工"));after.add(roleIds.get(primary));
      if(!before.equals(after)){
        new ManagementAccess(db).replaceRoles(uid,after,db.queryForObject("SELECT id FROM users WHERE username='admin'",Long.class));
        db.update("INSERT INTO authorization_changes(target_type,target_id,action,detail) VALUES('USER',?,'ROLE_POLICY_V2',?)",uid,"岗位映射修正："+before+" -> "+after);corrected++;
      }
    }
    for(String role:List.of("系统管理员","公司领导","部门经理","项目经理")){
      var permissions=new LinkedHashSet<>(List.of("user.read","user.roles","role.read","org.read","app.read","handover.start","handover.complete"));
      if(role.equals("公司领导"))permissions.add("org.write");
      if(role.equals("系统管理员"))permissions.addAll(ManagementAccess.PERMISSIONS.keySet());
      for(String permission:permissions)db.update("INSERT IGNORE INTO role_management_permissions(role_id,permission_code) VALUES(?,?)",roleIds.get(role),permission);
    }
    db.update("INSERT INTO user_role_scopes(user_id,role_id,scope_type) SELECT ur.user_id,ur.role_id,'GLOBAL' FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE r.code='系统管理员' AND r.enabled=1 AND u.status='ACTIVE'");
    db.update("INSERT INTO user_role_scopes(user_id,role_id,scope_type,company_id,department_id) SELECT ur.user_id,ur.role_id,IF(r.code='公司领导','COMPANY','DEPARTMENT'),d.company_id,IF(r.code='公司领导',NULL,d.id) FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id JOIN departments d ON d.id=u.department_id WHERE r.code IN ('公司领导','部门经理','项目经理') AND r.enabled=1 AND u.status='ACTIVE'");
    new ManagementAccess(db).retainPlatformAdmin();
    db.update("UPDATE management_migrations SET completed=1 WHERE version=2");
    System.out.println("角色范围迁移完成，修正初始化角色人数="+corrected);
  }
}
