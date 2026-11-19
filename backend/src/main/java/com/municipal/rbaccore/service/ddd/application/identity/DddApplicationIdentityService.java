package com.municipal.rbaccore.service.ddd.application.identity;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.ApplicationAuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.*;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.identity.*;
import com.municipal.rbaccore.service.port.security.*;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通用身份用例仅编排领域仓储与纯领域投影，不依赖JPA或任何课程模型。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.identity.enabled", havingValue = "true")
public class DddApplicationIdentityService implements ApplicationIdentityUseCase {
  private final AuthenticationUseCase authentication;
  private final IdentityTokenStore tokens;
  private final AuthorizationRepository authorization;
  private final UserRepository users;
  private final ApplicationSystemRepository systems;
  private final RequestContext requests;

  /** 外部应用的角色资格来自登记授权，与管理端准入分开。 */
  @Override
  public ApplicationLoginResult login(LoginCommand command, String system) {
    requireSystem(system);
    requests.application(system);
    CurrentIdentity actor = authentication.verify(command);
    view(actor.id(), system);
    return new ApplicationLoginResult(
        tokens.issueForSystem(actor.id(), system), "Bearer", tokens.expiresIn());
  }

  /** 每次重新装配领域事实，撤权不等待令牌过期。 */
  @Override
  public ApplicationIdentityResult current(String header) {
    String system = tokens.system(header);
    if (RbacCatalog.SYSTEM_CODE.equals(system)) throw BusinessException.unauthorized();
    requireSystem(system);
    requests.application(system);
    return view(authentication.requireForSystem(header, system).id(), system);
  }

  /** 只检查当前应用投影，不接收客户端自报角色。 */
  @Override
  public boolean allowed(String header, String code) {
    return current(header).permissions().contains(code);
  }

  /** 查询身份前检查本应用实际映射，不把特定应用权限码写入领域。 */
  @Override
  public ApplicationUserResult user(String header, Long id) {
    current(header);
    String system = tokens.system(header);
    AuthorizationFacts facts = facts(tokens.requireForSystem(header, system));
    boolean allowed =
        ApplicationAuthorizationPolicy.exported(facts, system).stream()
            .flatMap(permission -> permission.apis().stream())
            .anyMatch(
                api -> api.route().equals(new ApiResource.Route("GET", "/auth/users/{userId}")));
    if (!allowed) throw BusinessException.forbidden();
    User user =
        users
            .find(new User.Key(id))
            .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "用户不存在"));
    return new ApplicationUserResult(user.id().toString(), user.username(), user.name());
  }

  /** 角色编码按实际授予关系输出，不推断教师、学生或管理员身份。 */
  private ApplicationIdentityResult view(Long id, String system) {
    AuthorizationFacts facts = facts(id);
    Set<Permission> direct = ApplicationAuthorizationPolicy.exported(facts, system);
    if (direct.isEmpty())
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "该账号没有此应用的有效授权");
    Set<Long> ids =
        direct.stream().map(Permission::id).collect(java.util.stream.Collectors.toSet());
    List<String> roles =
        facts.user().roles().stream()
            .filter(Role::enabled)
            .filter(
                role ->
                    role.permissions().stream()
                        .anyMatch(permission -> ids.contains(permission.id())))
            .map(Role::code)
            .distinct()
            .sorted()
            .toList();
    java.util.Map<String, List<RbacCatalog.Route>> apiFacts =
        direct.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Permission::code,
                    permission ->
                        permission.apis().stream()
                            .map(
                                api ->
                                    new RbacCatalog.Route(api.route().method(), api.route().path()))
                            .sorted(
                                java.util.Comparator.comparing(RbacCatalog.Route::method)
                                    .thenComparing(RbacCatalog.Route::path))
                            .toList()));
    return new ApplicationIdentityResult(
        id.toString(),
        roles,
        direct.stream().map(Permission::code).sorted().toList(),
        system,
        apiFacts);
  }

  /** 领域事实始终来自当前账号，不缓存授权快照。 */
  private AuthorizationFacts facts(Long id) {
    return authorization.facts(new User.Key(id)).orElseThrow(BusinessException::unauthorized);
  }

  /** 管理端入口保持原规则；外部应用启停由领域目录事实决定。 */
  private void requireSystem(String code) {
    if (RbacCatalog.SYSTEM_CODE.equals(code))
      throw BusinessException.bad("INVALID_ARGUMENT", "管理端请使用管理登录接口");
    if (systems.catalog().stream()
        .noneMatch(system -> code.equals(system.code()) && system.enabled()))
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "应用未登记或已停用");
  }
}
