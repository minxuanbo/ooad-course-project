package com.municipal.rbaccore.bootstrap;

import com.municipal.rbaccore.importing.ImportPlan;
import com.municipal.rbaccore.importing.WorkbookImportPlanner;
import com.municipal.rbaccore.security.RbacCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 在写库前生成完整清单及摘要，规则/原件变化不能伪装成相同初始化。 */
@Component
public class RoleBootstrapPlanner {

  /** 五角色定义共享；其他业务系统的Excel权限不在RBAC自身初始化中展开。 */
  public RoleBootstrapPlan plan(Path directory) {
    ImportPlan source = new WorkbookImportPlanner().read(directory);
    List<RoleBootstrapPlan.Assignment> assignments =
        source.employees().stream()
            .map(
                person ->
                    new RoleBootstrapPlan.Assignment(
                        person.username().trim(), person.row(), InitialRoleRules.classify(person)))
            .toList();
    Map<String, Long> counts = new LinkedHashMap<>();
    for (InitialRole role : InitialRole.values()) {
      counts.put(
          role.getName(),
          role == InitialRole.SYSTEM_ADMIN
              ? 1L
              : assignments.stream().filter(item -> item.role() == role).count());
    }
    StringBuilder fingerprint =
        new StringBuilder(source.organizationSha256()).append(source.employeeSha256());
    for (RoleBootstrapPlan.Assignment assignment : assignments) {
      fingerprint
          .append('\n')
          .append(assignment.username())
          .append(':')
          .append(assignment.role().getCode());
    }
    RbacCatalog.definitions()
        .forEach(
            (permission, routes) ->
                routes.forEach(
                    route ->
                        fingerprint
                            .append('\n')
                            .append(permission)
                            .append(':')
                            .append(route.method())
                            .append(':')
                            .append(route.path())));
    try {
      String digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8)));
      return new RoleBootstrapPlan(source, List.copyOf(assignments), Map.copyOf(counts), digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("运行环境缺少SHA-256");
    }
  }
}
