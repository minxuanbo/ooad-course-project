package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import java.util.Set;
import java.util.stream.Collectors;

/** 通用应用短权限投影，完全依据领域目录及映射，不含接入应用的业务码。 */
public final class ApplicationAuthorizationPolicy {
  /** 无映射权限可表示菜单或资格，有映射则全部同系统启用才可导出粗粒度短码。 */
  public static Set<Permission> exported(AuthorizationFacts facts, String system) {
    return AuthorizationPolicy.directPermissions(facts, Set.of(system)).stream()
        .filter(permission -> permission.system().connected())
        .filter(
            permission -> permission.type() != Permission.Type.API || !permission.apis().isEmpty())
        .filter(
            permission ->
                permission.apis().stream()
                    .allMatch(
                        api ->
                            api.enabled()
                                && api.system().enabled()
                                && permission.system().id().equals(api.system().id())))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 无状态策略不建立实例。 */
  private ApplicationAuthorizationPolicy() {}
}
