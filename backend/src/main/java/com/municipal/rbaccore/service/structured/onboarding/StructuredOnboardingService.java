package com.municipal.rbaccore.service.structured.onboarding;

import com.municipal.rbaccore.infrastructure.onboarding.OperationMetadataJson;
import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.onboarding.*;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.resource.SystemResult;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.structured.security.StructuredGlobalManagementAccess;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.*;

/** 结构化接入事务脚本，直接JPA读写并独立执行完整关联校验。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StructuredOnboardingService implements OnboardingUseCase {

  private final ApplicationSystemJpaRepository systems;
  private final PermissionJpaRepository permissions;
  private final ApiResourceJpaRepository apis;
  private final RoleJpaRepository roles;
  private final ApplicationProfileJpaRepository profiles;
  private final ApiOperationJpaRepository operations;
  private final OperationMetadataJson json;

  private final StructuredGlobalManagementAccess access;
  private final AuthenticationUseCase authentication;
  private final ManagementAuthorization authorization;
  private final OnboardingCodec codec;
  private final OpenApiReader reader;
  private final IdentityProtocolProbe probe;

  /** 当前操作实际API、可信根与有效五级都必须成立。 */
  @Override
  public void checkTarget(String header, Long id) {
    access.require(header);
    if (!Integer.valueOf(5).equals(authentication.me(header).getManagementLevel()))
      throw BusinessException.scopeDenied();
    if (id != null) select(id);
  }

  /** 快照在同一RR事务内，指纹覆盖将要维护的完整配置。 */
  @Override
  public OnboardingSnapshot snapshot(String header, Long id) {
    checkTarget(header, id);
    return current(select(id));
  }

  /** 规范解析为只读预览，不修改角色。 */
  @Override
  public List<OnboardingPlan.Operation> preview(String header, Long id, String document) {
    checkTarget(header, id);
    return reader.read(document);
  }

  /** 自动检查是当前配置的真实协议探测，未发布草稿不伪装成生效。 */
  @Override
  public OnboardingReport check(
      String header, Long id, OnboardingPlan desired, String account, String password) {
    checkTarget(header, id);
    ApplicationSystemEntity system = select(id);
    OnboardingSnapshot current = current(system);
    match(current.plan(), desired);
    List<String> errors = problems(current.plan(), desired, current.roles());
    List<OnboardingReport.Check> checks =
        List.of(new OnboardingReport.Check("TEST_ACCOUNT", "PENDING", "未填写测试账号；可在完成接入后自动复检", null));
    if (errors.isEmpty() && account != null && !account.isBlank()) {
      checks = probe.run(system.getCode(), account, password);
      if (system.getConnectionStatus() == ConnectionStatus.PENDING)
        checks =
            checks.stream()
                .map(
                    check ->
                        check.httpStatus() != null && check.httpStatus() == 403
                            ? new OnboardingReport.Check(
                                check.code(),
                                "PENDING",
                                "待接入应用按预期拒绝登录，完成启用后自动复检",
                                check.httpStatus())
                            : check)
                .toList();
    }
    return new OnboardingReport(errors.isEmpty(), errors, checks);
  }

  /** 根锁内重验身份、指纹和所有关联；新授权不会提高人员级别。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OnboardingSnapshot complete(
      String header,
      Long id,
      OnboardingPlan desired,
      boolean businessConfirmed,
      boolean revocationConfirmed) {
    try {
      return access.write(
          header,
          () -> {
            checkTarget(header, id);
            ApplicationSystemEntity system = select(id);
            OnboardingSnapshot before = current(system);
            match(before.plan(), desired);
            List<String> errors = problems(before.plan(), desired, before.roles());
            if (!system.isEnabled()) errors = new ArrayList<>(List.of("请先恢复已移除应用"));
            if (!businessConfirmed || !revocationConfirmed)
              throw BusinessException.bad(
                  "ONBOARDING_CONFIRMATION_REQUIRED", "请确认业务后端对象权限和专用环境撤权测试");
            if (!errors.isEmpty())
              throw BusinessException.bad("ONBOARDING_INVALID", String.join("；", errors));
            if (grantsChanged(before.plan(), desired)
                && !authorization.allows(
                    authentication.require(header).id(), "PUT", "/api/roles/{id}/permissions"))
              throw BusinessException.forbidden();
            persist(system, desired);
            return current(system);
          });
    } catch (DataAccessException | TransactionException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "接入配置保存冲突，请刷新重试");
    }
  }

  /** 当前应用及系统角色是事实，不接受客户端自报管理员标记。 */
  private ApplicationSystemEntity select(Long id) {
    ApplicationSystemEntity system =
        systems.findById(id).orElseThrow(BusinessException::scopeDenied);
    if (system.getCode().equals("rbac"))
      throw new BusinessException(409, "ONBOARDING_RBAC_RESERVED", "RBAC自身请在资源与API中维护");
    return system;
  }

  /** 内容指纹不缓存权限，只阻止旧草稿覆盖别人刚保存的配置。 */
  private void match(OnboardingPlan current, OnboardingPlan desired) {
    if (!current.baseFingerprint().equals(desired.baseFingerprint()))
      throw new BusinessException(409, "ONBOARDING_STALE", "配置已变化，请重新打开向导");
  }

  /** 只有实际变化的角色权限子集需要额外的角色授权API。 */
  private boolean grantsChanged(OnboardingPlan current, OnboardingPlan desired) {
    Map<Long, Set<String>> grants = new HashMap<>();
    for (OnboardingPlan.Grant grant : current.roleGrants())
      grants.put(grant.roleId(), Set.copyOf(grant.permissionCodes()));
    return desired.roleGrants().stream()
        .anyMatch(grant -> !Set.copyOf(grant.permissionCodes()).equals(grants.get(grant.roleId())));
  }

  /** 保留所有已有目录，检查树、独立登录项、映射和角色子集。 */
  private List<String> problems(
      OnboardingPlan current, OnboardingPlan desired, List<OnboardingSnapshot.RoleOption> roles) {

    List<String> errors = new ArrayList<>();
    Map<String, OnboardingPlan.Api> apiIndex = new LinkedHashMap<>();
    for (OnboardingPlan.Api api : desired.apis())
      if (apiIndex.putIfAbsent(api.key(), api) != null) errors.add("API方法和路径重复：" + api.key());
    Map<String, OnboardingPlan.Permission> permissionIndex = new LinkedHashMap<>();
    for (OnboardingPlan.Permission permission : desired.permissions())
      if (permissionIndex.putIfAbsent(permission.code(), permission) != null)
        errors.add("权限编码重复：" + permission.code());
    for (OnboardingPlan.Api api : current.apis())
      if (!apiIndex.containsKey(api.key())) errors.add("已有API须保留，可显式停用：" + api.key());
    for (OnboardingPlan.Permission permission : current.permissions())
      if (!permissionIndex.containsKey(permission.code()))
        errors.add("已有权限须保留，可显式停用：" + permission.code());
    for (OnboardingPlan.Permission permission : desired.permissions()) {
      if (new HashSet<>(permission.apiKeys()).size() != permission.apiKeys().size())
        errors.add("API映射重复：" + permission.code());
      for (String key : permission.apiKeys())
        if (!apiIndex.containsKey(key)) errors.add("API映射不属于当前应用草稿：" + key);
      if (permission.enabled()
          && permission.type().equals("API")
          && (permission.apiKeys().isEmpty()
              || permission.apiKeys().stream()
                  .anyMatch(key -> !apiIndex.containsKey(key) || !apiIndex.get(key).enabled())))
        errors.add("启用API权限须完整映射启用接口：" + permission.code());
      Set<String> visited = new HashSet<>();
      String cursor = permission.code();
      while (cursor != null && !cursor.isBlank()) {
        if (!visited.add(cursor)) {
          errors.add("权限树形成循环：" + permission.code());
          break;
        }
        OnboardingPlan.Permission node = permissionIndex.get(cursor);
        if (node == null) {
          errors.add("父权限不在当前应用：" + cursor);
          break;
        }
        cursor = node.parentCode();
        if (cursor != null
            && !cursor.isBlank()
            && permissionIndex.containsKey(cursor)
            && !permissionIndex.get(cursor).type().equals("MENU")) {
          errors.add("父权限必须是菜单：" + cursor);
          break;
        }
      }
    }
    OnboardingPlan.Permission login = permissionIndex.get(desired.loginPermissionCode());
    if (login == null
        || !login.enabled()
        || login.type().equals("API")
        || !login.apiKeys().isEmpty()) errors.add("请选择启用且不映射业务API的菜单或按钮作为独立登录资格");
    Set<String> operationIds = new HashSet<>();
    Set<String> roleCodes =
        roles.stream()
            .map(OnboardingSnapshot.RoleOption::code)
            .collect(java.util.stream.Collectors.toSet());
    for (OnboardingPlan.Operation operation : desired.operations()) {
      if (!operationIds.add(operation.operationId()))
        errors.add("operationId重复：" + operation.operationId());
      if (new HashSet<>(operation.permissionRoles()).size() != operation.permissionRoles().size()
          || !roleCodes.containsAll(operation.permissionRoles()))
        errors.add("操作条件角色不存在或重复：" + operation.operationId());
      if (operation.requiresPermission()) {
        String key = operation.method() + " " + operation.path();
        if (!apiIndex.containsKey(key)) errors.add("受控操作缺少本应用API：" + operation.operationId());
        else if (apiIndex.get(key).enabled()
            && desired.permissions().stream()
                .noneMatch(
                    permission -> permission.enabled() && permission.apiKeys().contains(key)))
          errors.add("受控操作缺少权限映射：" + operation.operationId());
      }
    }
    Set<Long> roleIds =
        roles.stream()
            .map(OnboardingSnapshot.RoleOption::id)
            .collect(java.util.stream.Collectors.toSet());
    Set<Long> selected = new HashSet<>();
    boolean loginGranted = false;
    for (OnboardingPlan.Grant grant : desired.roleGrants()) {
      if (!selected.add(grant.roleId()) || !roleIds.contains(grant.roleId()))
        errors.add("角色不存在或重复：" + grant.roleId());
      if (new HashSet<>(grant.permissionCodes()).size() != grant.permissionCodes().size()
          || !permissionIndex.keySet().containsAll(grant.permissionCodes()))
        errors.add("角色权限不属于本应用草稿或重复：" + grant.roleId());
      if (grant.permissionCodes().contains(desired.loginPermissionCode())
          && roles.stream().anyMatch(role -> role.id().equals(grant.roleId()) && role.enabled()))
        loginGranted = true;
    }
    if (!loginGranted) errors.add("至少一个启用角色需要被明确授予登录资格");
    return errors.stream().distinct().toList();
  }

  /** 一次读取本应用完整配置，角色集合只表达权限定义，不附带人员。 */
  private OnboardingSnapshot current(ApplicationSystemEntity system) {

    List<ApiResourceEntity> apiRows = apis.findBySystemIdOrderByIdAsc(system.getId());
    List<PermissionEntity> permissionRows = permissions.findBySystemIdOrderByIdAsc(system.getId());
    List<RoleEntity> roleRows = roles.findAll(Sort.by("id"));
    ApplicationProfileEntity profile = profiles.findById(system.getId()).orElse(null);
    List<OnboardingPlan.Api> apiValues =
        apiRows.stream()
            .map(
                row ->
                    new OnboardingPlan.Api(
                        row.getHttpMethod(), row.getPathTemplate(), row.isEnabled()))
            .sorted(Comparator.comparing(OnboardingPlan.Api::key))
            .toList();
    List<OnboardingPlan.Permission> permissionValues =
        permissionRows.stream()
            .map(
                row ->
                    new OnboardingPlan.Permission(
                        row.getCode(),
                        row.getName(),
                        row.getType().name(),
                        row.getParent() == null ? null : row.getParent().getCode(),
                        row.isEnabled(),
                        row.getApis().stream()
                            .map(api -> api.getHttpMethod() + " " + api.getPathTemplate())
                            .sorted()
                            .toList()))
            .sorted(Comparator.comparing(OnboardingPlan.Permission::code))
            .toList();
    List<OnboardingPlan.Operation> operationValues =
        operations.findBySystemIdOrderByIdOperationIdAsc(system.getId()).stream()
            .map(
                row ->
                    new OnboardingPlan.Operation(
                        row.getId().getOperationId(),
                        row.getHttpMethod(),
                        row.getPathTemplate(),
                        row.isRequiresPermission(),
                        json.read(row.getPermissionRoles()).stream().sorted().toList()))
            .toList();
    List<OnboardingPlan.Grant> grants =
        roleRows.stream()
            .map(
                row ->
                    new OnboardingPlan.Grant(
                        row.getId(),
                        row.getPermissions().stream()
                            .filter(
                                permission -> permission.getSystem().getId().equals(system.getId()))
                            .map(PermissionEntity::getCode)
                            .sorted()
                            .toList()))
            .toList();

    OnboardingPlan plan =
        new OnboardingPlan(
            "",
            profile == null ? null : profile.getEntryUrl(),
            profile == null ? null : profile.getLoginPermissionCode(),
            apiValues,
            permissionValues,
            operationValues,
            grants);
    SystemResult view = cn.edu.xmu.clonefactory.util.CloneFactory.copy(new SystemResult(), system);
    view.setEnabled(system.isEnabled());
    List<String> facts =
        new ArrayList<>(
            List.of(
                system.getId().toString(),
                system.getCode(),
                system.getName(),
                Boolean.toString(system.isEnabled()),
                system.getConnectionStatus().name()));
    for (RoleEntity role : roleRows)
      facts.addAll(
          List.of(
              role.getId().toString(),
              role.getCode(),
              role.getName(),
              role.getType().name(),
              Boolean.toString(role.isEnabled())));
    return new OnboardingSnapshot(
        view,
        plan.withFingerprint(codec.fingerprint(plan, facts)),
        roleRows.stream()
            .map(
                row ->
                    new OnboardingSnapshot.RoleOption(
                        row.getId(),
                        row.getCode(),
                        row.getName(),
                        row.getType().name(),
                        row.isEnabled()))
            .toList());
  }

  /** 写入只做差量，其他应用的权限和所有人员角色保持原样。 */
  private void persist(ApplicationSystemEntity system, OnboardingPlan desired) {

    Map<String, ApiResourceEntity> apiRows = new LinkedHashMap<>();
    for (ApiResourceEntity row : apis.findBySystemIdOrderByIdAsc(system.getId()))
      apiRows.put(row.getHttpMethod() + " " + row.getPathTemplate(), row);
    for (OnboardingPlan.Api value : desired.apis()) {
      ApiResourceEntity row = apiRows.get(value.key());
      if (row == null) {
        row = new ApiResourceEntity(system, value.method(), value.path());
        apiRows.put(value.key(), row);
      }
      row.setEnabled(value.enabled());
      apis.save(row);
    }
    Map<String, PermissionEntity> permissionRows = new LinkedHashMap<>();
    for (PermissionEntity row : permissions.findBySystemIdOrderByIdAsc(system.getId())) {
      row.moveTo(null);
      permissionRows.put(row.getCode(), row);
    }
    for (OnboardingPlan.Permission value : desired.permissions()) {
      PermissionEntity row = permissionRows.get(value.code());
      if (row == null) {
        row =
            new PermissionEntity(
                system, value.code(), value.name(), PermissionType.valueOf(value.type()));
        permissionRows.put(value.code(), row);
      }
      row.rename(value.name());
      row.changeType(PermissionType.valueOf(value.type()));
      row.setEnabled(value.enabled());
      permissions.save(row);
    }
    for (OnboardingPlan.Permission value : desired.permissions()) {
      PermissionEntity row = permissionRows.get(value.code());
      row.moveTo(
          value.parentCode() == null || value.parentCode().isBlank()
              ? null
              : permissionRows.get(value.parentCode()));
      Set<ApiResourceEntity> desiredApis =
          value.apiKeys().stream().map(apiRows::get).collect(java.util.stream.Collectors.toSet());
      for (ApiResourceEntity old : new ArrayList<>(row.getApis()))
        if (!desiredApis.contains(old)) row.unmapApi(old);
      for (ApiResourceEntity selected : desiredApis)
        if (!row.getApis().contains(selected)) row.mapApi(selected);
      permissions.save(row);
    }
    for (OnboardingPlan.Grant grant : desired.roleGrants()) {
      RoleEntity row = roles.findById(grant.roleId()).orElseThrow();
      Set<PermissionEntity> selected =
          grant.permissionCodes().stream()
              .map(permissionRows::get)
              .collect(java.util.stream.Collectors.toSet());
      for (PermissionEntity old : new ArrayList<>(row.getPermissions()))
        if (old.getSystem().getId().equals(system.getId()) && !selected.contains(old))
          row.revokePermission(old);
      for (PermissionEntity permission : selected)
        if (!row.getPermissions().contains(permission)) row.grantPermission(permission);
      roles.save(row);
    }
    Set<String> ids =
        desired.operations().stream()
            .map(OnboardingPlan.Operation::operationId)
            .collect(java.util.stream.Collectors.toSet());
    List<ApiOperationEntity> removed =
        operations.findBySystemIdOrderByIdOperationIdAsc(system.getId()).stream()
            .filter(row -> !ids.contains(row.getId().getOperationId()))
            .toList();
    operations.deleteAll(removed);
    for (OnboardingPlan.Operation value : desired.operations())
      operations.save(
          new ApiOperationEntity(
              system,
              value.operationId(),
              value.method(),
              value.path(),
              value.requiresPermission(),
              json.write(value.permissionRoles())));
    ApplicationProfileEntity profile =
        profiles
            .findById(system.getId())
            .orElseGet(() -> new ApplicationProfileEntity(system, null, null));
    profile.revise(
        desired.entryUrl() == null || desired.entryUrl().isBlank() ? null : desired.entryUrl(),
        desired.loginPermissionCode());
    profiles.save(profile);
    system.setConnectionStatus(ConnectionStatus.CONNECTED);
    systems.saveAndFlush(system);
  }
}
