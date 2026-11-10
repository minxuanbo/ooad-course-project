package com.municipal.rbaccore.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 已评审的RBAC自身目录，与Excel中系统管理后台等业务系统分开。 */
public final class RbacCatalog {
  public static final String SYSTEM_CODE = "rbac";
  public static final Set<String> PERSONNEL =
      Set.of(
          "rbac.users.read",
          "rbac.users.roles.write",
          "rbac.users.disable",
          "rbac.users.department.write");

  private RbacCatalog() {}

  /** 只声明已评审的API模板；后续任务接入各自Controller，不生成通用CRUD。 */
  public static Map<String, List<Route>> definitions() {
    Map<String, List<Route>> definitions = new LinkedHashMap<>();
    definitions.put(
        "rbac.users.read",
        List.of(
            route("GET", "/api/departments/tree"),
            route("GET", "/api/users"),
            route("GET", "/api/users/{id}/roles")));
    definitions.put(
        "rbac.users.roles.write",
        List.of(
            route("GET", "/api/users/{id}/assignable-roles"),
            route("PUT", "/api/users/{id}/roles")));
    definitions.put("rbac.users.disable", List.of(route("PATCH", "/api/users/{id}/status")));
    definitions.put(
        "rbac.users.department.write", List.of(route("PUT", "/api/users/{id}/department")));
    definitions.put(
        "rbac.resources.read",
        List.of(
            route("GET", "/api/systems"),
            route("GET", "/api/permissions/tree"),
            route("GET", "/api/api-resources"),
            route("GET", "/api/permissions/{id}/apis")));
    definitions.put(
        "rbac.resources.write",
        List.of(
            route("PATCH", "/api/permissions/{id}"),
            route("PATCH", "/api/api-resources/{id}"),
            route("PUT", "/api/permissions/{id}/apis")));
    definitions.put(
        "rbac.roles.read",
        List.of(route("GET", "/api/roles"), route("GET", "/api/roles/{id}/permissions")));
    definitions.put(
        "rbac.roles.write",
        List.of(route("POST", "/api/roles"), route("PATCH", "/api/roles/{id}")));
    definitions.put(
        "rbac.roles.permissions.write", List.of(route("PUT", "/api/roles/{id}/permissions")));
    definitions.put("rbac.logs.read", List.of(route("GET", "/api/authorization-logs")));
    return java.util.Collections.unmodifiableMap(definitions);
  }

  /** 登录准入与实际请求都使用同一方法及模板键，不以角色或岗位名称判断。 */
  public static Set<Route> managementRoutes() {
    return definitions().values().stream()
        .flatMap(List::stream)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** K不包含日志、停用人员和调动，其余已评审模板均须完整保留。 */
  public static Set<Route> maintenanceRoutes() {
    return definitions().entrySet().stream()
        .filter(
            entry ->
                !Set.of("rbac.logs.read", "rbac.users.disable", "rbac.users.department.write")
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
