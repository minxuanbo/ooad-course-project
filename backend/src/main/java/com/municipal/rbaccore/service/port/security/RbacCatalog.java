package com.municipal.rbaccore.service.port.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 已评审的RBAC自身目录，与Excel中系统管理后台等业务系统分开。 */
public final class RbacCatalog {
  public static final String SYSTEM_CODE = "rbac";
  public static final Route LOGIN_ROUTE = new Route("POST", "/api/auth/login");
  public static final Set<String> PERSONNEL =
      Set.of(
          "rbac.login",
          "rbac.users.read",
          "rbac.roles.read",
          "rbac.roles.permissions.read",
          "rbac.departments.tree.read",
          "rbac.users.roles.read",
          "rbac.users.roles.candidates.read",
          "rbac.users.roles.write",
          "rbac.users.disable",
          "rbac.users.department.write",
          "rbac.overview.read",
          "rbac.departments.read",
          "rbac.departments.write",
          "rbac.departments.create",
          "rbac.departments.delete");

  private RbacCatalog() {}

  /** 每个操作独立映射一个真实API；同页按钮不隐式捆绑或互相继承。 */
  public static Map<String, List<Route>> definitions() {
    Map<String, List<Route>> definitions = new LinkedHashMap<>();
    definitions.put("rbac.overview.read", List.of(route("GET", "/api/overview")));
    definitions.put("rbac.departments.read", List.of(route("GET", "/api/departments")));
    definitions.put("rbac.departments.create", List.of(route("POST", "/api/departments")));
    definitions.put("rbac.departments.write", List.of(route("PATCH", "/api/departments/{id}")));
    definitions.put("rbac.departments.delete", List.of(route("DELETE", "/api/departments/{id}")));
    definitions.put("rbac.departments.tree.read", List.of(route("GET", "/api/departments/tree")));
    definitions.put("rbac.users.read", List.of(route("GET", "/api/users")));
    definitions.put("rbac.users.roles.read", List.of(route("GET", "/api/users/{id}/roles")));
    definitions.put(
        "rbac.users.roles.candidates.read",
        List.of(route("GET", "/api/users/{id}/assignable-roles")));
    definitions.put("rbac.users.roles.write", List.of(route("PUT", "/api/users/{id}/roles")));
    definitions.put("rbac.users.disable", List.of(route("PATCH", "/api/users/{id}/status")));
    definitions.put(
        "rbac.users.department.write", List.of(route("PUT", "/api/users/{id}/department")));
    definitions.put("rbac.systems.read", List.of(route("GET", "/api/systems")));
    definitions.put("rbac.resources.read", List.of(route("GET", "/api/permissions/tree")));
    definitions.put("rbac.apis.read", List.of(route("GET", "/api/api-resources")));
    definitions.put(
        "rbac.resources.apis.read", List.of(route("GET", "/api/permissions/{id}/apis")));
    definitions.put("rbac.resources.write", List.of(route("PATCH", "/api/permissions/{id}")));
    definitions.put("rbac.apis.write", List.of(route("PATCH", "/api/api-resources/{id}")));
    definitions.put(
        "rbac.resources.apis.write", List.of(route("PUT", "/api/permissions/{id}/apis")));
    definitions.put("rbac.roles.read", List.of(route("GET", "/api/roles")));
    definitions.put(
        "rbac.roles.permissions.read", List.of(route("GET", "/api/roles/{id}/permissions")));
    definitions.put("rbac.roles.create", List.of(route("POST", "/api/roles")));
    definitions.put("rbac.roles.write", List.of(route("PATCH", "/api/roles/{id}")));
    definitions.put(
        "rbac.roles.permissions.write", List.of(route("PUT", "/api/roles/{id}/permissions")));
    definitions.put("rbac.logs.read", List.of(route("GET", "/api/authorization-logs")));
    definitions.put("rbac.login", List.of(LOGIN_ROUTE));
    definitions.put("rbac.systems.create", List.of(route("POST", "/api/systems")));
    definitions.put("rbac.systems.page.read", List.of(route("GET", "/api/systems/page")));
    definitions.put("rbac.systems.write", List.of(route("PATCH", "/api/systems/{id}")));
    definitions.put("rbac.systems.remove", List.of(route("DELETE", "/api/systems/{id}")));
    definitions.put(
        "rbac.systems.onboarding.read", List.of(route("GET", "/api/systems/{id}/onboarding")));
    definitions.put(
        "rbac.systems.onboarding.import",
        List.of(route("POST", "/api/systems/{id}/onboarding/import")));
    definitions.put(
        "rbac.systems.onboarding.check",
        List.of(route("POST", "/api/systems/{id}/onboarding/check")));
    definitions.put(
        "rbac.systems.onboarding.complete",
        List.of(route("POST", "/api/systems/{id}/onboarding/complete")));
    return java.util.Collections.unmodifiableMap(definitions);
  }

  /** 提供通俗的中文展示名称；编码用于稳定关联，不按展示名称鉴权。 */
  public static Map<String, String> displayNames() {
    return Map.ofEntries(
        Map.entry("rbac.login", "登录RBAC"),
        Map.entry("rbac.systems.onboarding.read", "查看接入向导"),
        Map.entry("rbac.systems.onboarding.import", "导入接入接口"),
        Map.entry("rbac.systems.onboarding.check", "检查接入配置"),
        Map.entry("rbac.systems.onboarding.complete", "完成应用接入"),
        Map.entry("rbac.systems.create", "新增受管应用"),
        Map.entry("rbac.systems.page.read", "查询受管应用"),
        Map.entry("rbac.systems.write", "维护应用资料与接入"),
        Map.entry("rbac.systems.remove", "移除受管应用"),
        Map.entry("rbac.overview.read", "查看总览"),
        Map.entry("rbac.departments.read", "查看部门目录"),
        Map.entry("rbac.departments.write", "修改部门名称"),
        Map.entry("rbac.departments.create", "新增下级部门"),
        Map.entry("rbac.departments.delete", "删除空部门"),
        Map.entry("rbac.departments.tree.read", "查看部门树"),
        Map.entry("rbac.users.read", "查询人员"),
        Map.entry("rbac.users.roles.read", "查看人员角色"),
        Map.entry("rbac.users.roles.candidates.read", "查询可授予角色"),
        Map.entry("rbac.users.roles.write", "分配人员角色"),
        Map.entry("rbac.users.disable", "停用人员"),
        Map.entry("rbac.users.department.write", "调动部门归属"),
        Map.entry("rbac.systems.read", "查看系统目录"),
        Map.entry("rbac.resources.read", "查看权限树"),
        Map.entry("rbac.apis.read", "查询API资源"),
        Map.entry("rbac.resources.apis.read", "查看API映射"),
        Map.entry("rbac.resources.write", "维护权限属性"),
        Map.entry("rbac.apis.write", "启停API资源"),
        Map.entry("rbac.resources.apis.write", "配置API映射"),
        Map.entry("rbac.roles.read", "查询角色"),
        Map.entry("rbac.roles.permissions.read", "查看角色权限"),
        Map.entry("rbac.roles.create", "新建角色"),
        Map.entry("rbac.roles.write", "修改角色"),
        Map.entry("rbac.roles.permissions.write", "配置角色权限"),
        Map.entry("rbac.logs.read", "查询鉴权日志"));
  }

  /** 七个页面菜单显式映射自己的读取API，不取得子按钮的写入能力。 */
  public static Map<String, String> menus() {
    Map<String, String> menus = new LinkedHashMap<>();
    menus.put("rbac.overview.menu", "总览");
    menus.put("rbac.systems.menu", "应用管理");
    menus.put("rbac.workspace", "人员管理");
    menus.put("rbac.roles.menu", "角色与权限");
    menus.put("rbac.resources.menu", "资源与API");
    menus.put("rbac.logs.menu", "鉴权日志");
    menus.put("rbac.departments.menu", "部门管理");
    return java.util.Collections.unmodifiableMap(menus);
  }

  /** 菜单显式映射页面的读取API，不包含该页写按钮，关联不是父子继承。 */
  public static Map<String, List<Route>> menuRoutes() {
    return Map.of(
        "rbac.overview.menu", List.of(route("GET", "/api/overview")),
        "rbac.workspace", List.of(route("GET", "/api/users")),
        "rbac.departments.menu", List.of(route("GET", "/api/departments")),
        "rbac.roles.menu", List.of(route("GET", "/api/roles")),
        "rbac.resources.menu",
            List.of(route("GET", "/api/permissions/tree"), route("GET", "/api/api-resources")),
        "rbac.systems.menu",
            List.of(route("GET", "/api/systems"), route("GET", "/api/systems/page")),
        "rbac.logs.menu", List.of(route("GET", "/api/authorization-logs")));
  }

  /** 目录父级只组织展示，不参与权限继承或组织管理范围判定。 */
  public static String parentCode(String code) {
    if (code.equals("rbac.login")) return null;
    if (code.equals("rbac.overview.read")) return "rbac.overview.menu";
    if (code.startsWith("rbac.systems.")) return "rbac.systems.menu";
    if (code.startsWith("rbac.users.") || code.equals("rbac.departments.tree.read"))
      return "rbac.workspace";
    if (code.startsWith("rbac.departments.")) return "rbac.departments.menu";
    if (code.startsWith("rbac.roles.")) return "rbac.roles.menu";
    if (code.startsWith("rbac.logs.")) return "rbac.logs.menu";
    return "rbac.resources.menu";
  }

  /** K保留原17项恢复维护模板，不包含日志、停用、调动及新增部门/总览操作。 */
  public static Set<Route> maintenanceRoutes() {
    return definitions().entrySet().stream()
        .filter(
            entry ->
                !Set.of(
                        "rbac.login",
                        "rbac.systems.onboarding.read",
                        "rbac.systems.onboarding.import",
                        "rbac.systems.onboarding.check",
                        "rbac.systems.onboarding.complete",
                        "rbac.systems.create",
                        "rbac.systems.page.read",
                        "rbac.systems.write",
                        "rbac.systems.remove",
                        "rbac.logs.read",
                        "rbac.users.disable",
                        "rbac.users.department.write",
                        "rbac.overview.read",
                        "rbac.departments.read",
                        "rbac.departments.write",
                        "rbac.departments.create",
                        "rbac.departments.delete")
                    .contains(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 保持目录定义简洁，实际请求路径来自Spring匹配结果。 */
  private static Route route(String method, String path) {
    return new Route(method, path);
  }

  /** API资源的真实HTTP方法与服务端路径模板。 */
  public record Route(String method, String path) {}
}
