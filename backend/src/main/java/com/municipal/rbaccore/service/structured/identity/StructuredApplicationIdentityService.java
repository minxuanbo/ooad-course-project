package com.municipal.rbaccore.service.structured.identity;

import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
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

/** 通用应用身份事务脚本，仅处理已登记应用的账号、角色与权限事实。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.identity.enabled", havingValue = "true")
public class StructuredApplicationIdentityService implements ApplicationIdentityUseCase {
  private final AuthenticationUseCase authentication;
  private final IdentityTokenStore tokens;
  private final PermissionJpaRepository permissions;
  private final ApplicationSystemJpaRepository systems;
  private final UserJpaRepository users;
  private final RequestContext requests;

  /** 凭据正确、应用启用且已有有效授权时签发独立受众令牌。 */
  @Override
  public ApplicationLoginResult login(LoginCommand command, String system) {
    requireSystem(system);
    requests.application(system);
    CurrentIdentity actor = authentication.verify(command);
    view(actor.id(), system);
    return new ApplicationLoginResult(
        tokens.issueForSystem(actor.id(), system), "Bearer", tokens.expiresIn());
  }

  /** 当前角色和权限由最新授权链计算，令牌只定位账号与受众。 */
  @Override
  public ApplicationIdentityResult current(String header) {
    String system = tokens.system(header);
    if (RbacCatalog.SYSTEM_CODE.equals(system)) throw BusinessException.unauthorized();
    requireSystem(system);
    requests.application(system);
    return view(authentication.requireForSystem(header, system).id(), system);
  }

  /** 未授予、停用或未知权限均返回false。 */
  @Override
  public boolean allowed(String header, String code) {
    return current(header).permissions().contains(code);
  }

  /** 身份最小查询由本应用的实际API映射控制，不内置任何业务权限短码。 */
  @Override
  public ApplicationUserResult user(String header, Long id) {
    current(header);
    Long actor = tokens.requireForSystem(header, tokens.system(header));
    boolean allowed =
        permissions.directPermissions(actor, Set.of(tokens.system(header))).stream()
            .filter(this::exportable)
            .flatMap(permission -> permission.getApis().stream())
            .anyMatch(
                api ->
                    "GET".equals(api.getHttpMethod())
                        && "/auth/users/{userId}".equals(api.getPathTemplate()));
    if (!allowed) throw BusinessException.forbidden();
    UserEntity user =
        users
            .findById(id)
            .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "用户不存在"));
    return new ApplicationUserResult(user.getId().toString(), user.getUsername(), user.getName());
  }

  /** 只有贡献当前应用有效直接权限的启用角色才导出其配置编码。 */
  private ApplicationIdentityResult view(Long id, String system) {
    UserEntity user = users.findById(id).orElseThrow(BusinessException::unauthorized);
    List<PermissionEntity> direct = permissions.directPermissions(id, Set.of(system));
    List<String> codes =
        direct.stream()
            .filter(this::exportable)
            .map(PermissionEntity::getCode)
            .distinct()
            .sorted()
            .toList();
    if (codes.isEmpty())
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "该账号没有此应用的有效授权");
    Set<Long> ids =
        direct.stream()
            .filter(this::exportable)
            .map(PermissionEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
    List<String> roles =
        user.getRoles().stream()
            .filter(RoleEntity::isEnabled)
            .filter(
                role ->
                    role.getPermissions().stream()
                        .anyMatch(permission -> ids.contains(permission.getId())))
            .map(RoleEntity::getCode)
            .distinct()
            .sorted()
            .toList();
    java.util.Map<String, List<RbacCatalog.Route>> apiFacts =
        direct.stream()
            .filter(this::exportable)
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    PermissionEntity::getCode,
                    permission ->
                        permission.getApis().stream()
                            .map(
                                api ->
                                    new RbacCatalog.Route(
                                        api.getHttpMethod(), api.getPathTemplate()))
                            .sorted(
                                java.util.Comparator.comparing(RbacCatalog.Route::method)
                                    .thenComparing(RbacCatalog.Route::path))
                            .toList()));
    return new ApplicationIdentityResult(id.toString(), roles, codes, system, apiFacts);
  }

  /** 无API的菜单与资格权限直接生效；有映射的短码要求全部映射同系统且启用。 */
  private boolean exportable(PermissionEntity permission) {
    return (permission.getType() != com.municipal.rbaccore.service.port.resource.PermissionType.API
            || !permission.getApis().isEmpty())
        && permission.getApis().stream()
            .allMatch(
                api ->
                    api.isEnabled()
                        && api.getSystem().isEnabled()
                        && api.getSystem().getId().equals(permission.getSystem().getId()));
  }

  /** 管理端必须沿原管理准入登录；外部应用按登记状态核实。 */
  private void requireSystem(String code) {
    if (RbacCatalog.SYSTEM_CODE.equals(code))
      throw BusinessException.bad("INVALID_ARGUMENT", "管理端请使用管理登录接口");
    if (systems.findByCode(code).filter(system -> system.isEnabled()).isEmpty())
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "应用未登记或已停用");
  }
}
