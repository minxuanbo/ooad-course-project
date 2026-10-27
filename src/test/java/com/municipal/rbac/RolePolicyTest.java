package com.municipal.rbac;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class RolePolicyTest {
  @Autowired JdbcTemplate db;
  @Test void sourcePositionsMapWithoutKeywordOverlap(){
    for(String title:List.of("项目经理","项目副经理","项目总工程师"))assertEquals("项目经理",RolePolicy.primary(title,"第一项目部",true));
    for(String title:List.of("部长","副部长","主任","副主任"))assertEquals("部门经理",RolePolicy.primary(title,"办公室",false));
    assertEquals("公司领导",RolePolicy.primary("未知职务","公司领导",false));
    assertEquals("公司领导",RolePolicy.primary("副经理","公司",true));
    assertEquals("部门经理",RolePolicy.primary("负责人","部门",true));
    assertEquals("普通员工",RolePolicy.primary("技术员","项目部",false));
    assertEquals("普通员工",RolePolicy.primary("审计专员","审计监察部",false));
    for(String title:List.of("部长","副部长","项目经理","项目副经理","项目总工程师")){
      String expected=title.startsWith("项目")?"项目经理":"部门经理";
      int total=db.queryForObject("SELECT count(*) FROM users WHERE position=?",Integer.class,title);
      assertTrue(total>0);
      assertEquals(total,db.queryForObject("SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.position=? AND r.code=?",Integer.class,title,expected));
    }
    assertEquals(0,db.queryForObject("SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.position IN ('项目经理','项目副经理','项目总工程师') AND r.code IN ('公司领导','部门经理')",Integer.class));
  }
  @Test void migrationCorrectsOnlyUneditedImportedRolesAndRunsOnce(){
    long dept=db.queryForObject("SELECT MIN(id) FROM departments",Long.class);
    for(String no:List.of("SG999991","SG999992")){
      db.update("INSERT INTO users(employee_no,username,name,department_id,position,password_hash) VALUES(?,?,?,?,'项目总工程师','unused')",no,no,no,dept);
      db.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r WHERE u.username=? AND r.code IN ('普通员工','公司领导','部门经理')",no);
    }
    long edited=db.queryForObject("SELECT id FROM users WHERE username='SG999992'",Long.class);
    db.update("INSERT INTO authorization_changes(target_type,target_id,action) VALUES('USER',?,'SET_ROLES')",edited);
    db.update("UPDATE management_migrations SET completed=0 WHERE version=2");
    db.update("DELETE FROM management_scopes");
    new ManagementBootstrap(db).run(null);
    assertEquals(List.of("普通员工","项目经理"),db.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE u.username='SG999991' ORDER BY r.code",String.class));
    assertEquals(3,db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=?",Integer.class,edited));
    int count=db.queryForObject("SELECT count(*) FROM management_scopes",Integer.class);
    new ManagementBootstrap(db).run(null);assertEquals(count,db.queryForObject("SELECT count(*) FROM management_scopes",Integer.class));
  }
}
