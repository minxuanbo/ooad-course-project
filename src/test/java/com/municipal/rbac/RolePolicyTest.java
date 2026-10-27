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
  @Autowired DataInitializer initializer;
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
  @Test void existingDatabaseRestartPreservesRevokedPermissionsAndScopes()throws Exception{
    db.update("DELETE FROM management_scopes");
    db.update("DELETE FROM role_management_permissions");
    initializer.run(null);
    assertEquals(0,db.queryForObject("SELECT count(*) FROM management_scopes",Integer.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM role_management_permissions",Integer.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='management_migrations'",Integer.class));
  }
}
