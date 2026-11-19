package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 把有效领域链投影为接入系统的粗粒度权限，任何部分路由失效均关闭整个短码。 */
public final class CourseAuthorizationPolicy {
  /** 纯领域计算只接收当前事实和外部协议值，不调用数据库或技术适配器。 */
  public static Set<String> exportedPermissions(
      AuthorizationFacts facts,
      String system,
      Map<String, Set<ApiResource.Route>> required,
      Set<String> displayCodes) {
    Set<String> result = new TreeSet<>();
    for (Permission permission : AuthorizationPolicy.directPermissions(facts, Set.of(system))) {
      if (displayCodes.contains(permission.code())) result.add(permission.code());
      Set<ApiResource.Route> routes = required.get(permission.code());
      if (routes == null || routes.isEmpty()) continue;
      Set<ApiResource.Route> actual =
          permission.apis().stream()
              .filter(
                  api ->
                      api.enabled()
                          && api.system().enabled()
                          && permission.system().id().equals(api.system().id()))
              .map(ApiResource::route)
              .collect(Collectors.toUnmodifiableSet());
      if (actual.containsAll(routes)) result.add(permission.code());
    }
    return Set.copyOf(result);
  }

  /** 无状态领域策略不允许构造。 */
  private CourseAuthorizationPolicy() {}
}
