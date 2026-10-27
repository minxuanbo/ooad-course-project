package com.municipal.rbac;

import java.util.*;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Idempotent infrastructure migration; DDL is intentionally outside business transactions. */
@Component @Order(5)
class DesignSchemaMigration implements ApplicationRunner {
  private final JdbcTemplate db;
  DesignSchemaMigration(JdbcTemplate db){this.db=db;}
  public void run(ApplicationArguments args){
    String aliases="SELECT username identifier,id user_id FROM users UNION SELECT employee_no,id FROM users UNION SELECT phone,id FROM users WHERE phone IS NOT NULL";
    if(db.queryForObject("SELECT count(*) FROM (SELECT identifier FROM ("+aliases+") a GROUP BY identifier HAVING count(DISTINCT user_id)>1) conflicts",Integer.class)>0)
      throw new IllegalStateException("存在跨用户登录标识冲突，禁止自动合并账号，请先核对标识");
    db.update("INSERT INTO login_identifiers(identifier,user_id) "+aliases+" ON DUPLICATE KEY UPDATE user_id=IF(login_identifiers.user_id=VALUES(user_id),login_identifiers.user_id,NULL)");
    String claims="INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.username,NEW.id),(NEW.employee_no,NEW.id) ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL); IF NEW.phone IS NOT NULL THEN INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.phone,NEW.id) ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL); END IF;";
    if(!trigger("users_identifier_insert"))db.execute("CREATE TRIGGER users_identifier_insert AFTER INSERT ON users FOR EACH ROW BEGIN "+claims+" END");
    if(!trigger("users_identifier_update"))db.execute("CREATE TRIGGER users_identifier_update AFTER UPDATE ON users FOR EACH ROW BEGIN IF NOT(OLD.username<=>NEW.username) OR NOT(OLD.employee_no<=>NEW.employee_no) OR NOT(OLD.phone<=>NEW.phone) THEN DELETE FROM login_identifiers WHERE user_id=NEW.id; "+claims+" END IF; END");
    if(table("user_role_scopes")){
      db.update("INSERT IGNORE INTO management_scopes(id,user_id,role_id,scope_type,company_id,department_id) SELECT id,user_id,role_id,scope_type,IF(scope_type='COMPANY',company_id,NULL),department_id FROM user_role_scopes");
      if(db.queryForObject("SELECT count(*) FROM user_role_scopes old LEFT JOIN management_scopes n ON n.id=old.id AND n.user_id=old.user_id AND n.role_id=old.role_id AND n.scope_type=old.scope_type AND n.department_id<=>old.department_id AND n.company_id<=>IF(old.scope_type='COMPANY',old.company_id,NULL) WHERE n.id IS NULL",Integer.class)>0)
        throw new IllegalStateException("角色范围迁移核对失败");
      db.execute("DROP TABLE user_role_scopes");
    }
    if(table("management_grants")){
      if(db.queryForObject("SELECT count(*) FROM management_grants old WHERE old.include_descendants<>0 OR NOT EXISTS(SELECT 1 FROM management_scopes g JOIN role_management_permissions p ON p.role_id=g.role_id WHERE g.user_id=old.user_id AND p.permission_code=old.permission_code AND g.scope_type=old.scope_type AND g.company_id<=>IF(old.scope_type='COMPANY',old.company_id,NULL) AND g.department_id<=>old.department_id)",Integer.class)>0)throw new IllegalStateException("存在未迁移的独立管理授权，禁止删除");
      db.execute("DROP TABLE management_grants");
    }
    if(column("departments","parent_id")){
      for(String name:db.queryForList("SELECT DISTINCT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='departments' AND column_name='parent_id' AND referenced_table_name IS NOT NULL",String.class))db.execute("ALTER TABLE departments DROP FOREIGN KEY `"+name.replace("`","``")+"`");
      db.execute("ALTER TABLE departments DROP COLUMN parent_id");
    }
    var columns=new LinkedHashMap<String,String>();columns.put("event_type","VARCHAR(32) NOT NULL DEFAULT 'AUTHORIZATION'");columns.put("http_method","VARCHAR(16) NOT NULL DEFAULT ''");columns.put("request_path","VARCHAR(512) NOT NULL DEFAULT ''");columns.put("http_status","INT");columns.put("archived_at","DATETIME(3)");columns.put("archived_by","BIGINT");
    columns.forEach((name,definition)->{if(!column("authorization_logs",name))db.execute("ALTER TABLE authorization_logs ADD COLUMN "+name+" "+definition);});
    var indexes=Map.of("idx_log_user_id","user_id,id","idx_log_app_id","app_code,id","idx_log_archive_time","archived_at,occurred_at,id");
    indexes.forEach((name,fields)->{if(db.queryForObject("SELECT count(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='authorization_logs' AND index_name=?",Integer.class,name)==0)db.execute("CREATE INDEX "+name+" ON authorization_logs("+fields+")");});
    db.update("INSERT INTO management_migrations(version,completed) VALUES(3,1) ON DUPLICATE KEY UPDATE completed=1");
  }
  private boolean trigger(String name){return db.queryForObject("SELECT count(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",Integer.class,name)>0;}
  private boolean table(String name){return db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,name)>0;}
  private boolean column(String table,String column){return db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",Integer.class,table,column)>0;}
}
