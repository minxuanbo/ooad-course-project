package com.municipal.rbac;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class IdentifierConstraintTest {
  @Autowired JdbcTemplate db; @Autowired AuthService auth;
  @Test void insertUpdateAndDeleteMaintainOneNamespaceAtomically(){
    db.update("INSERT INTO users(employee_no,username,name,phone,password_hash) VALUES('id-number','id-login','测试','100000001','unused')");
    long id=db.queryForObject("SELECT id FROM users WHERE username='id-login'",Long.class);
    assertEquals(3,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE user_id=?",Integer.class,id));
    for(String sql:new String[]{"INSERT INTO users(employee_no,username,name,password_hash) VALUES('id-login','other-login','测试','unused')","INSERT INTO users(employee_no,username,name,password_hash) VALUES('other-no','100000001','测试','unused')","UPDATE users SET phone='id-login' WHERE username='SG010000'"})assertThrows(DataIntegrityViolationException.class,()->db.update(sql));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM users WHERE username='id-login'",Integer.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE identifier='other-no'",Integer.class));
    db.update("UPDATE users SET username=employee_no,phone=NULL WHERE id=?",id);
    assertEquals(1,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE user_id=?",Integer.class,id));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE identifier IN ('id-login','100000001')",Integer.class));
    db.update("UPDATE users SET name='改名' WHERE id=?",id);
    assertEquals(1,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE user_id=?",Integer.class,id));
    db.update("DELETE FROM users WHERE id=?",id);assertEquals(0,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE user_id=?",Integer.class,id));
  }
  @Test void aliasesAuthenticateTheSameAccountAndDepartmentScopeDoesNotRepeatCompany(){
    var user=db.queryForMap("SELECT id,employee_no,phone FROM users WHERE username='SG010000'");
    for(String field:new String[]{"employee_no","phone"})assertEquals(user.get("id"),auth.login((String)user.get(field),"123456").user().id());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM management_scopes WHERE scope_type='DEPARTMENT' AND company_id IS NOT NULL",Integer.class));
    var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE management_scopes SET company_id=(SELECT company_id FROM departments WHERE id=department_id) WHERE scope_type='DEPARTMENT' LIMIT 1"));
    assertEquals(3819,((java.sql.SQLException)failure.getCause()).getErrorCode());
  }
  @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentCrossFieldClaimsHaveExactlyOneOwner()throws Exception{
    var start=new java.util.concurrent.CountDownLatch(1);
    var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      var a=executor.submit(()->{start.await();try{db.update("INSERT INTO users(employee_no,username,name,password_hash) VALUES('race-shared','race-a','并发A','unused')");return true;}catch(org.springframework.dao.DataAccessException e){return false;}});
      var b=executor.submit(()->{start.await();try{db.update("INSERT INTO users(employee_no,username,name,password_hash) VALUES('race-b','race-shared','并发B','unused')");return true;}catch(org.springframework.dao.DataAccessException e){return false;}});
      start.countDown();assertNotEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1,db.queryForObject("SELECT count(*) FROM login_identifiers WHERE identifier='race-shared'",Integer.class));
      assertEquals(1,db.queryForObject("SELECT count(*) FROM users WHERE employee_no='race-shared' OR username='race-shared'",Integer.class));
    }finally{executor.shutdownNow();db.update("DELETE FROM users WHERE username IN ('race-a','race-shared')");}
  }
}
