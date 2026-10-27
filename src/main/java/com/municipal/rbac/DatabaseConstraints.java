package com.municipal.rbac;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Complete fresh-schema constraints before importing employees. */
@Component @Order(0)
class DatabaseConstraints implements ApplicationRunner {
  private final JdbcTemplate db;
  DatabaseConstraints(JdbcTemplate db){this.db=db;}
  public void run(ApplicationArguments args){
    if(db.queryForObject("SELECT count(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='departments' AND column_name='manager_user_id' AND referenced_table_name='users'",Integer.class)==0)
      db.execute("ALTER TABLE departments ADD CONSTRAINT fk_department_manager FOREIGN KEY(manager_user_id) REFERENCES users(id)");
    String claims="INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.username,NEW.id),(NEW.employee_no,NEW.id) ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL); IF NEW.phone IS NOT NULL THEN INSERT INTO login_identifiers(identifier,user_id) VALUES(NEW.phone,NEW.id) ON DUPLICATE KEY UPDATE user_id=IF(user_id=NEW.id,user_id,NULL); END IF;";
    if(!trigger("users_identifier_insert"))db.execute("CREATE TRIGGER users_identifier_insert AFTER INSERT ON users FOR EACH ROW BEGIN "+claims+" END");
    if(!trigger("users_identifier_update"))db.execute("CREATE TRIGGER users_identifier_update AFTER UPDATE ON users FOR EACH ROW BEGIN IF NOT(OLD.username<=>NEW.username) OR NOT(OLD.employee_no<=>NEW.employee_no) OR NOT(OLD.phone<=>NEW.phone) THEN DELETE FROM login_identifiers WHERE user_id=NEW.id; "+claims+" END IF; END");
  }
  private boolean trigger(String name){return db.queryForObject("SELECT count(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",Integer.class,name)>0;}
}
