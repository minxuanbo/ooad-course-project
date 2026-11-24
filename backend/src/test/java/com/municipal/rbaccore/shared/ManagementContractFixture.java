package com.municipal.rbaccore.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 只在人员测试专库建立根、经理和员工，供运行期管理HTTP契约复用。 */
public final class ManagementContractFixture {
  public static final String PASSWORD = "isolated-stage4-fixture";

  private ManagementContractFixture() {}

  /** 每次构造完整独立夹具，不读取或修改真实业务库。 */
  public static void prepare(JdbcTemplate jdbc, PasswordEncoder encoder) {
    assertEquals("rbac_core_t11_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    jdbc.update("UPDATE permissions SET parent_id=NULL");
    for (String table :
        List.of(
            "api_operations",
            "application_profiles",
            "authorization_logs",
            "bootstrap_receipts",
            "import_sources",
            "user_roles",
            "role_permissions",
            "permission_apis",
            "users",
            "roles",
            "permissions",
            "api_resources",
            "systems")) jdbc.update("DELETE FROM " + table);
    jdbc.update("UPDATE departments SET parent_id=NULL");
    jdbc.update("DELETE FROM departments");
    jdbc.update("INSERT INTO departments(id,code,name) VALUES(1,'root','总公司')");
    jdbc.update(
        "INSERT INTO departments(id,code,name,parent_id)"
            + " VALUES(2,'dept','工程部',1),(3,'project','项目',2)");
    jdbc.update(
        "INSERT INTO import_sources(source_type,source_key,source_json,department_id)"
            + " VALUES('ORGANIZATION','总公司','[]',1)");
    jdbc.update(
        "INSERT INTO systems(id,code,name,enabled)"
            + " VALUES(1,'rbac','RBAC',1),(2,'business','业务系统',1)");
    jdbc.update(
        "INSERT INTO roles(id,code,name,enabled)"
            + " VALUES(1,'system-admin','管理员',1),(2,'department-manager','经理',1),(3,'employee','员工',1),(4,'custom','自定义',1)");
    jdbc.update(
        "UPDATE roles SET role_type=CASE WHEN id<=3 THEN 'SYSTEM_BUILTIN' ELSE 'GLOBAL_CUSTOM' END,"
            + " management_level=CASE id WHEN 1 THEN 5 WHEN 2 THEN 3 WHEN 3 THEN 1 ELSE NULL END");
    jdbc.update(
        "UPDATE departments SET type=CASE id WHEN 1 THEN 'HEADQUARTERS' WHEN 3 THEN 'PROJECT' ELSE"
            + " 'DEPARTMENT' END");
    long next = 1;
    for (Map.Entry<String, List<RbacCatalog.Route>> entry : RbacCatalog.definitions().entrySet()) {
      long permission = next++;
      jdbc.update(
          "INSERT INTO permissions(id,system_id,code,name,type,enabled) VALUES(?,1,?,?,'BUTTON',1)",
          permission,
          entry.getKey(),
          entry.getKey());
      jdbc.update("INSERT INTO role_permissions VALUES(1,?)", permission);
      if (RbacCatalog.PERSONNEL.contains(entry.getKey()))
        jdbc.update("INSERT INTO role_permissions VALUES(2,?)", permission);
      for (RbacCatalog.Route route : entry.getValue()) {
        long api = next++;
        jdbc.update(
            "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
                + " VALUES(?,1,?,?,1)",
            api,
            route.method(),
            route.path());
        jdbc.update("INSERT INTO permission_apis VALUES(?,?)", permission, api);
      }
    }
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(999,2,'sensitive','业务操作','BUTTON',1)");
    jdbc.update("INSERT INTO role_permissions VALUES(3,999)");
    for (int id : List.of(1, 2, 3, 4))
      jdbc.update(
          "INSERT INTO users(id,username,password_hash,name,department_id,enabled,position)"
              + " VALUES(?,?,?,?,?,1,'只读职位')",
          id,
          "stage4-" + id,
          encoder.encode(PASSWORD),
          "人员" + id,
          id == 1 ? 1 : 2);
    jdbc.update("INSERT INTO user_roles VALUES(1,1),(2,2),(3,2),(4,3)");
  }
}
