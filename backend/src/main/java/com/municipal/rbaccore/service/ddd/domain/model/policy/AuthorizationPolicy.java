package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 逐条计算有效授权链，多条独立有效链按OR合并。 */
public final class AuthorizationPolicy {
  private AuthorizationPolicy() {}

  /** 停用用户不产生任何权限；已经启用的直接子项不受祖先状态影响。 */
  public static Set<Permission> directPermissions(AuthorizationFacts facts) {
    return directPermissions(facts, Set.of(ApplicationSystem.CODE_RBAC));
  }

  /** 授予子集包含显式接入目录中的完整有效权限，不要求菜单映射API。 */
  public static Set<Permission> directPermissions(AuthorizationFacts facts, Set<String> systems) {
    if (!facts.user().enabled()) return Set.of();
    return facts.user().roles().stream()
        .filter(Role::enabled)
        .flatMap(role -> role.permissions().stream())
        .filter(permission -> active(permission, systems))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 直接有效权限不要求存在可执行API，人员授予检查使用完整集合。 */
  public static boolean activeRbac(Permission permission) {
    return active(permission, Set.of(ApplicationSystem.CODE_RBAC));
  }

  /** 系统归属和启用必须同时成立，不能把未知系统过滤成空权限。 */
  public static boolean active(Permission permission, Set<String> systems) {
    return permission.enabled()
        && permission.system().enabled()
        && systems.contains(permission.system().code());
  }

  /** 只返回能实际到达同系统启用API的直接权限。 */
  public static Set<Permission> effectivePermissions(AuthorizationFacts facts) {
    return directPermissions(facts).stream()
        .filter(permission -> activeApis(permission).findAny().isPresent())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 路由身份包含方法和服务端路径模板，业务角色名称不参与。 */
  public static Set<ApiResource.Route> routes(AuthorizationFacts facts) {
    return directPermissions(facts).stream()
        .flatMap(AuthorizationPolicy::activeApis)
        .map(ApiResource::route)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 跨系统污染映射和停用API不向当前权限提供路由。 */
  private static Stream<ApiResource> activeApis(Permission permission) {
    return permission.apis().stream()
        .filter(
            api ->
                api.enabled()
                    && api.system().enabled()
                    && permission.system().id().equals(api.system().id()));
  }
}
