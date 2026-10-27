package com.municipal.rbac;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class DesignMigrationTest {
  @Autowired JdbcTemplate real;
  @Test void legacyScopeCopiesAndVerifiesBeforeDroppingRedundantFields(){
    long id=9000000;
    var scope=real.queryForMap("SELECT g.user_id,g.role_id,g.department_id,d.company_id FROM management_scopes g JOIN departments d ON d.id=g.department_id WHERE g.scope_type='DEPARTMENT' LIMIT 1");
    real.execute("CREATE TABLE user_role_scopes(id BIGINT PRIMARY KEY,user_id BIGINT,role_id BIGINT,scope_type VARCHAR(16),company_id BIGINT,department_id BIGINT)");
    real.update("INSERT INTO user_role_scopes VALUES(?,?,?,'DEPARTMENT',?,?)",id,scope.get("user_id"),scope.get("role_id"),scope.get("company_id"),scope.get("department_id"));
    real.execute("CREATE TABLE management_grants(id BIGINT,user_id BIGINT,permission_code VARCHAR(64),scope_type VARCHAR(16),company_id BIGINT,department_id BIGINT,include_descendants TINYINT DEFAULT 0)");
    real.update("INSERT INTO management_grants(user_id,permission_code,scope_type,company_id,department_id) VALUES(?,'user.read','DEPARTMENT',?,?)",scope.get("user_id"),scope.get("company_id"),scope.get("department_id"));
    real.execute("ALTER TABLE departments ADD parent_id BIGINT, ADD CONSTRAINT test_parent FOREIGN KEY(parent_id) REFERENCES departments(id)");
    try{
      new DesignSchemaMigration(real).run(null);
      var migrated=real.queryForMap("SELECT * FROM management_scopes WHERE id=?",id);assertNull(migrated.get("company_id"));assertEquals(scope.get("department_id"),migrated.get("department_id"));
      assertEquals(0,real.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('user_role_scopes','management_grants')",Integer.class));
      new DesignSchemaMigration(real).run(null);assertEquals(1,real.queryForObject("SELECT count(*) FROM management_scopes WHERE id=?",Integer.class,id));
    }finally{real.update("DELETE FROM management_scopes WHERE id=?",id);}
  }
  @Test void conflictingAliasesAndUnverifiedCopiesFailClosed(){
    JdbcTemplate db=mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(1);
    assertThrows(IllegalStateException.class,()->new DesignSchemaMigration(db).run(null));verify(db,never()).execute(anyString());
    reset(db);when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0,1);
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(1);
    assertThrows(IllegalStateException.class,()->new DesignSchemaMigration(db).run(null));verify(db,never()).execute(startsWith("DROP"));
    reset(db);when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0,0,1);
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(1);
    assertThrows(IllegalStateException.class,()->new DesignSchemaMigration(db).run(null));verify(db,never()).execute("DROP TABLE management_grants");
  }
  @Test void missingColumnsAndIndexesAreInstalledAndQuotedForeignKeysAreSafe(){
    JdbcTemplate db=mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(0);
    when(db.queryForObject(anyString(),eq(Integer.class),anyString(),anyString())).thenReturn(0);
    when(db.queryForObject(anyString(),eq(Integer.class),eq("departments"),eq("parent_id"))).thenReturn(1);
    when(db.queryForList(anyString(),eq(String.class))).thenReturn(List.of("odd`key"));
    new DesignSchemaMigration(db).run(null);
    verify(db).execute("ALTER TABLE departments DROP FOREIGN KEY `odd``key`");
    verify(db).execute(contains("ADD COLUMN event_type"));verify(db).execute(startsWith("CREATE INDEX idx_log_user_id"));
    verify(db).execute(startsWith("CREATE TRIGGER users_identifier_insert"));verify(db).execute(startsWith("CREATE TRIGGER users_identifier_update"));
  }
}
