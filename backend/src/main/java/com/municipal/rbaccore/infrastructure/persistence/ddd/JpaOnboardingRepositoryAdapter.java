package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.onboarding.OperationMetadataJson;
import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.OnboardingRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** 将领域接入聚合保存到JPA，关联采用差集，不在此决定组织范围。 */
@Repository
@RequiredArgsConstructor
public class JpaOnboardingRepositoryAdapter implements OnboardingRepository {

  private final ApplicationSystemJpaRepository systems;
  private final PermissionJpaRepository permissions;
  private final ApiResourceJpaRepository apis;
  private final RoleJpaRepository roles;
  private final ApplicationProfileJpaRepository profiles;
  private final ApiOperationJpaRepository operations;
  private final OperationMetadataJson json;

  /** 装配完整当前领域事实，应用是否允许编辑由应用服务判断。 */
  @Override
  public Optional<ApplicationOnboarding> find(ApplicationOnboarding.Key key) {
    return systems.findById(key.systemId()).map(this::load);
  }

  /** 仓储只返回领域类型，技术元数据JSON在适配层处理。 */
  private ApplicationOnboarding load(ApplicationSystemEntity system) {

    List<ApiResourceEntity> apiRows = apis.findBySystemIdOrderByIdAsc(system.getId());
    List<PermissionEntity> permissionRows = permissions.findBySystemIdOrderByIdAsc(system.getId());
    List<RoleEntity> roleRows = roles.findAll(Sort.by("id"));
    ApplicationProfileEntity profile = profiles.findById(system.getId()).orElse(null);
    List<ApplicationOnboarding.Api> apiValues =
        apiRows.stream()
            .map(
                row ->
                    new ApplicationOnboarding.Api(
                        row.getHttpMethod(), row.getPathTemplate(), row.isEnabled()))
            .sorted(Comparator.comparing(ApplicationOnboarding.Api::key))
            .toList();
    List<ApplicationOnboarding.Permission> permissionValues =
        permissionRows.stream()
            .map(
                row ->
                    new ApplicationOnboarding.Permission(
                        row.getCode(),
                        row.getName(),
                        Permission.Type.valueOf(row.getType().name()),
                        row.getParent() == null ? null : row.getParent().getCode(),
                        row.isEnabled(),
                        row.getApis().stream()
                            .map(api -> api.getHttpMethod() + " " + api.getPathTemplate())
                            .sorted()
                            .toList()))
            .sorted(Comparator.comparing(ApplicationOnboarding.Permission::code))
            .toList();
    List<ApplicationOnboarding.Operation> operationValues =
        operations.findBySystemIdOrderByIdOperationIdAsc(system.getId()).stream()
            .map(
                row ->
                    new ApplicationOnboarding.Operation(
                        row.getId().getOperationId(),
                        row.getHttpMethod(),
                        row.getPathTemplate(),
                        row.isRequiresPermission(),
                        json.read(row.getPermissionRoles()).stream().sorted().toList()))
            .toList();
    List<ApplicationOnboarding.Grant> grants =
        roleRows.stream()
            .map(
                row ->
                    new ApplicationOnboarding.Grant(
                        row.getId(),
                        row.getPermissions().stream()
                            .filter(
                                permission -> permission.getSystem().getId().equals(system.getId()))
                            .map(PermissionEntity::getCode)
                            .sorted()
                            .toList()))
            .toList();

    ApplicationSystem application =
        new ApplicationSystem(
            system.getId(),
            system.getCode(),
            system.getName(),
            system.isEnabled(),
            ApplicationSystem.Connection.valueOf(system.getConnectionStatus().name()));
    ApplicationOnboarding.Configuration configuration =
        new ApplicationOnboarding.Configuration(
            profile == null ? null : profile.getEntryUrl(),
            profile == null ? null : profile.getLoginPermissionCode(),
            apiValues,
            permissionValues,
            operationValues,
            grants);
    return new ApplicationOnboarding(
        application,
        configuration,
        roleRows.stream()
            .map(
                row ->
                    new ApplicationOnboarding.RoleOption(
                        row.getId(),
                        row.getCode(),
                        row.getName(),
                        row.getType().name(),
                        row.isEnabled()))
            .toList());
  }

  /** 数据库并发或约束错误在保存边界翻译，事务整体回滚。 */
  @Override
  public void save(ApplicationOnboarding value) {
    try {
      persist(value);
    } catch (DataAccessException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "接入配置保存冲突，请刷新重试");
    }
  }

  /** 不删除权限、API或人员角色；本应用角色子集按实际差量维护。 */
  private void persist(ApplicationOnboarding aggregate) {
    ApplicationSystemEntity system = systems.findById(aggregate.application().id()).orElseThrow();
    ApplicationOnboarding.Configuration desired = aggregate.configuration();

    Map<String, ApiResourceEntity> apiRows = new LinkedHashMap<>();
    for (ApiResourceEntity row : apis.findBySystemIdOrderByIdAsc(system.getId()))
      apiRows.put(row.getHttpMethod() + " " + row.getPathTemplate(), row);
    for (ApplicationOnboarding.Api value : desired.apis()) {
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
    for (ApplicationOnboarding.Permission value : desired.permissions()) {
      PermissionEntity row = permissionRows.get(value.code());
      if (row == null) {
        row =
            new PermissionEntity(
                system, value.code(), value.name(), PermissionType.valueOf(value.type().name()));
        permissionRows.put(value.code(), row);
      }
      row.rename(value.name());
      row.changeType(PermissionType.valueOf(value.type().name()));
      row.setEnabled(value.enabled());
      permissions.save(row);
    }
    for (ApplicationOnboarding.Permission value : desired.permissions()) {
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
    for (ApplicationOnboarding.Grant grant : desired.roleGrants()) {
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
            .map(ApplicationOnboarding.Operation::operationId)
            .collect(java.util.stream.Collectors.toSet());
    List<ApiOperationEntity> removed =
        operations.findBySystemIdOrderByIdOperationIdAsc(system.getId()).stream()
            .filter(row -> !ids.contains(row.getId().getOperationId()))
            .toList();
    operations.deleteAll(removed);
    for (ApplicationOnboarding.Operation value : desired.operations())
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
