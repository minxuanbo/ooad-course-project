package com.municipal.rbac;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Framework entry point only; migration is an explicit, backed-up maintenance operation.
@Component @Order(0)
class SchemaGuard implements ApplicationRunner {
  private final JdbcTemplate db;
  SchemaGuard(JdbcTemplate db){this.db=db;}
  public void run(ApplicationArguments args){
    if(db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='company'",Integer.class)>0)
      throw new IllegalStateException("旧数据库尚未迁移。停止服务并执行 scripts/normalize_mysql.py --database rbac --apply（先预检与备份）。");
    if(db.queryForObject("SELECT count(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='departments' AND column_name='manager_user_id' AND referenced_table_name='users'",Integer.class)==0)
      db.execute("ALTER TABLE departments ADD CONSTRAINT fk_department_manager FOREIGN KEY(manager_user_id) REFERENCES users(id)");
  }
}
