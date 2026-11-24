package com.municipal.rbaccore.service.ddd.application.security;

import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.model.policy.AuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提供当前请求的领域授权计算，无跨请求权限缓存。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddAuthorizationService implements ManagementAuthorization {
  private final AuthorizationRepository authorization;

  /** 将领域路由投影为公共方法和模板契约。 */
  @Override
  public Set<RbacCatalog.Route> routes(Long userId) {
    return authorization.facts(new User.Key(userId)).stream()
        .flatMap(facts -> AuthorizationPolicy.routes(facts).stream())
        .map(route -> new RbacCatalog.Route(route.method(), route.path()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 实际请求只匹配服务端路径模板与HTTP方法。 */
  @Override
  public boolean allows(Long userId, String method, String template) {
    return routes(userId).contains(new RbacCatalog.Route(method, template));
  }

  /** 登录入口需要独立有效映射，不从业务菜单反推登录资格。 */
  @Override
  public boolean canEnter(Long userId) {
    return routes(userId).contains(RbacCatalog.LOGIN_ROUTE);
  }
}
