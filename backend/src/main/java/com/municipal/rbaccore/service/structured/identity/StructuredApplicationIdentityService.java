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
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.security.*;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import com.municipal.rbaccore.service.structured.security.StructuredEffectivePermissionService;
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
  private final StructuredEffectivePermissionService effective;
  private final ApplicationSystemJpaRepository systems;
  private final UserJpaRepository users;
  private final RequestContext requests;
  private final IdentityDirectoryGateway directory;
  private final ApiOperationCatalog operations;
  private final StructuredDepartmentScopeService scope;

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
    boolean allowed = current(header).permissions().contains(code);
    requests.permissionCheck(allowed);
    return allowed;
  }

  /** 身份最小查询由本应用的实际API映射控制，不内置任何业务权限短码。 */
  @Override
  public ApplicationUserResult user(String header, Long id) {
    requireRoute(header, "/auth/users/{userId}");
    if (id == null) throw new BusinessException(404, "IDENTITY_USER_NOT_FOUND", "统一身份用户不存在");
    UserEntity user =
        users
            .findById(id)
            .orElseThrow(() -> new BusinessException(404, "IDENTITY_USER_NOT_FOUND", "统一身份用户不存在"));
    return new ApplicationUserResult(user.getId().toString(), user.getUsername(), user.getName());
  }

  /** 撤销当前外部身份不要求仍有业务授权，不会销毁另一个应用的会话。 */
  @Override
  public void logout(String header) {
    String system = tokens.system(header);
    if (RbacCatalog.SYSTEM_CODE.equals(system)) throw BusinessException.unauthorized();
    tokens.requireForSystem(header, system);
    requests.application(system);
    tokens.revoke(header);
  }

  /** 组织目录的计数和列表在同一快照下，仅按一层父级及关键词查询。 */
  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public ApplicationDirectoryPage<ApplicationDepartmentResult> departments(
      String header, Long parentId, String keyword, int page, int pageSize) {
    CurrentIdentity actor = requireRoute(header, "/auth/departments");
    Set<Long> visible = Set.copyOf(scope.allowedIds(actor));
    if (parentId != null) requireDepartment(actor, parentId, visible);
    return directory.departments(actor.departmentId(), parentId, visible, keyword, page, pageSize);
  }

  /** 部门用户不展开下级，目标范围与查询API授权分别校验。 */
  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public ApplicationDirectoryPage<ApplicationDepartmentUserResult> departmentUsers(
      String header, Long departmentId, String keyword, int page, int pageSize) {
    CurrentIdentity actor = requireRoute(header, "/auth/departments/{departmentId}/users");
    requireDepartment(actor, departmentId, Set.copyOf(scope.allowedIds(actor)));
    return directory.users(departmentId, keyword, page, pageSize);
  }

  /** 读取操作元数据后用当前直接映射判断，全局放行不代替课程对象级授权。 */
  @Override
  public boolean apiAllowed(String header, String operationId) {
    ApplicationIdentityResult identity = current(header);
    ApiOperationCatalog.Operation operation =
        operations
            .find(identity.system(), operationId)
            .orElseThrow(
                () -> new BusinessException(404, "API_OPERATION_NOT_FOUND", "没有找到对应的API操作"));
    boolean gated =
        operation.requiresPermission()
            && (operation.permissionRoles().isEmpty()
                || identity.roles().stream().anyMatch(operation.permissionRoles()::contains));
    boolean allowed =
        !gated
            || hasRoute(
                tokens.requireForSystem(header, identity.system()),
                identity.system(),
                operation.method(),
                operation.path());
    requests.permissionCheck(allowed);
    return allowed;
  }

  /** 实际目录或查人操作必须有本应用API映射，最终判定统一进入鉴权日志。 */
  private CurrentIdentity requireRoute(String header, String path) {
    ApplicationIdentityResult identity = current(header);
    CurrentIdentity actor = authentication.requireForSystem(header, identity.system());
    boolean allowed = hasRoute(actor.id(), identity.system(), "GET", path);
    requests.authorizationDecision(allowed);
    if (!allowed) throw BusinessException.forbidden();
    return actor;
  }

  /** API必须来自同应用、启用且完整有效的直接权限映射。 */
  private boolean hasRoute(Long actor, String system, String method, String path) {
    return permissions
        .directPermissions(actor, effective.applicableRoleIds(actor), Set.of(system))
        .stream()
        .filter(this::exportable)
        .flatMap(permission -> permission.getApis().stream())
        .anyMatch(api -> method.equals(api.getHttpMethod()) && path.equals(api.getPathTemplate()));
  }

  /** 局部账号不能探测范围外目标是否存在，共同根才能区分未知部门。 */
  private void requireDepartment(CurrentIdentity actor, Long id, Set<Long> visible) {
    if (!visible.contains(id)) {
      if (!scope.isGlobal(actor) || directory.department(id).isPresent()) {
        requests.authorizationDecision(false);
        throw BusinessException.scopeDenied();
      }
      throw new BusinessException(404, "IDENTITY_DEPARTMENT_NOT_FOUND", "统一身份部门不存在");
    }
    if (directory.department(id).isEmpty()) {
      throw new BusinessException(404, "IDENTITY_DEPARTMENT_NOT_FOUND", "统一身份部门不存在");
    }
  }

  /** 只有贡献当前应用有效直接权限的启用角色才导出其配置编码。 */
  private ApplicationIdentityResult view(Long id, String system) {
    UserEntity user = users.findById(id).orElseThrow(BusinessException::unauthorized);
    Set<Long> applicableRoles = effective.applicableRoleIds(id);
    List<PermissionEntity> direct =
        permissions.directPermissions(id, applicableRoles, Set.of(system));
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
            .filter(role -> applicableRoles.contains(role.getId()))
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
    return permission.getSystem().isEnabled()
        && permission.getSystem().getConnectionStatus()
            == com.municipal.rbaccore.service.port.resource.ConnectionStatus.CONNECTED
        && (permission.getType() != PermissionType.API || !permission.getApis().isEmpty())
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
    if (systems
        .findByCode(code)
        .filter(
            system ->
                system.isEnabled() && system.getConnectionStatus() == ConnectionStatus.CONNECTED)
        .isEmpty())
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "应用未登记、未启用或尚未完成接入");
  }
}
