package com.municipal.rbaccore.service.ddd.application.auth;

import com.municipal.rbaccore.service.ddd.application.support.DddScope;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.model.policy.AuthenticationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.AuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.security.*;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本地账号认证及本人信息查询，逐请求获取状态和动态能力。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddAuthenticationService implements AuthenticationUseCase {
  private final UserRepository users;
  private final AuthorizationRepository facts;
  private final PasswordHashing passwords;
  private final IdentityTokenStore tokens;
  private final ManagementAuthorization authorization;
  private final RequestContext requests;
  private final DddScope scope;

  /** 同一工号或手机号定位唯一启用账号，再依当前权限判断管理准入。 */
  @Override
  public LoginResult login(LoginCommand command) {
    CurrentIdentity actor = verify(command);
    if (!authorization.canEnter(actor.id())) {
      throw new BusinessException(403, "MANAGEMENT_ACCESS_DENIED", "该账号没有本权限系统的有效管理权限");
    }
    User user = active(actor.id());
    return new LoginResult(tokens.issue(user.id()), result(user), tokens.expiresIn());
  }

  /** 使用领域匹配和口令端口核实身份，不在凭据校验时签发应用身份。 */
  @Override
  public CurrentIdentity verify(LoginCommand command) {
    requests.actor(null, null);
    User matched =
        AuthenticationPolicy.eligibleAccount(
                users.match(new User.LoginIdentity(command.getAccount().trim())))
            .filter(user -> passwords.matches(command.getPassword(), user.passwordHash()))
            .orElseThrow(() -> new BusinessException(401, "LOGIN_FAILED", "账号或密码错误"));
    requests.actor(matched.id(), matched.username());
    return new CurrentIdentity(matched.id(), matched.username(), matched.department().id());
  }

  /** 令牌定位主体，不携带启停、部门或权限快照。 */
  @Override
  public CurrentIdentity require(String header) {
    return requireForSystem(header, RbacCatalog.SYSTEM_CODE);
  }

  /** 领域账号状态与令牌受众分别校验，课程会话不能进入管理身份接口。 */
  @Override
  public CurrentIdentity requireForSystem(String header, String system) {
    User user = active(tokens.requireForSystem(header, system));
    requests.actor(user.id(), user.username());
    return new CurrentIdentity(user.id(), user.username(), user.department().id());
  }

  /** 本人接口只要求当前启用身份。 */
  @Override
  public IdentityResult me(String header) {
    return result(active(require(header).id()));
  }

  /** 本人权限只包含实际可达API的直接项，按稳定ID排序。 */
  @Override
  public List<EffectivePermissionResult> myPermissions(String header) {
    User.Key user = new User.Key(require(header).id());
    return AuthorizationPolicy.effectivePermissions(
            facts.facts(user).orElseThrow(BusinessException::unauthorized))
        .stream()
        .sorted(Comparator.comparing(Permission::id))
        .map(this::permissionResult)
        .toList();
  }

  /** 沿公共令牌存储幂等撤销会话。 */
  @Override
  public void logout(String header) {
    tokens.revoke(header);
  }

  /** 从当前事务读取启用账号。 */
  private User active(Long id) {
    return users
        .find(new User.Key(id))
        .filter(User::enabled)
        .orElseThrow(BusinessException::unauthorized);
  }

  /** 领域记录显式映射，权限及全局标记由当前事实派生。 */
  private IdentityResult result(User user) {
    IdentityResult identity = new IdentityResult();
    identity.setId(user.id());
    identity.setUsername(user.username());
    identity.setName(user.name());
    identity.setEnabled(user.enabled());
    identity.setDepartmentName(user.department().name());
    identity.setDepartmentId(user.department().id());
    identity.setManagementLevel(user.level());
    identity.setGlobalScope(OrganizationPolicy.global(scope.facts(), user.department().id()));
    identity.setRoutes(
        authorization.routes(user.id()).stream()
            .sorted(
                Comparator.comparing(RbacCatalog.Route::path)
                    .thenComparing(RbacCatalog.Route::method))
            .toList());
    return identity;
  }

  /** 返回公共权限形状，关系编号和枚举显式处理。 */
  private EffectivePermissionResult permissionResult(Permission permission) {
    EffectivePermissionResult result = new EffectivePermissionResult();
    result.setId(permission.id());
    result.setSystemId(permission.system().id());
    result.setParentId(permission.parentId());
    result.setCode(permission.code());
    result.setName(permission.name());
    result.setType(PermissionType.valueOf(permission.type().name()));
    return result;
  }
}
