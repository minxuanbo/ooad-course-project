package com.municipal.rbaccore.service.ddd.application.bootstrap;

import com.municipal.rbaccore.service.ddd.application.support.DddImportManifest;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.InitialAuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.InitializationRepository;
import com.municipal.rbaccore.service.port.bootstrap.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.importing.SourceWorkbookReader;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 预览与首次授权使用同一领域分类和来源校验，回执保护已有调整。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddInitialAuthorizationService implements AuthorizationBootstrapUseCase {
  private static final Map<Integer, InitialRole> INITIAL_ROLES =
      Arrays.stream(InitialRole.values())
          .collect(
              Collectors.toUnmodifiableMap(InitialRole::getManagementLevel, Function.identity()));
  private static final Map<Integer, String> CODES =
      INITIAL_ROLES.entrySet().stream()
          .collect(
              Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().getCode()));
  private static final List<BootstrapPlan.PermissionSeed> PERMISSIONS =
      RbacCatalog.definitions().entrySet().stream()
          .map(
              entry ->
                  new BootstrapPlan.PermissionSeed(
                      entry.getKey(),
                      RbacCatalog.displayNames().get(entry.getKey()),
                      entry.getValue().stream()
                          .map(route -> new ApiResource.Route(route.method(), route.path()))
                          .toList(),
                      RbacCatalog.PERSONNEL.contains(entry.getKey())))
          .toList();

  private final SourceWorkbookReader source;
  private final InitializationRepository initialization;
  private final MaintenanceProtection maintenance;
  private final Clock clock;

  /** 文件预览在只读快照核对，不写目录、角色、用户或回执。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RoleBootstrapPlan preview(Path directory) {
    ImportPlan sourcePlan = source.read(directory);
    BootstrapPlan plan = plan(sourcePlan);
    validate(plan);
    List<RoleBootstrapPlan.Assignment> assignments =
        plan.employees().stream()
            .map(
                item ->
                    new RoleBootstrapPlan.Assignment(
                        item.username(), item.sourceRow(), INITIAL_ROLES.get(item.level())))
            .toList();
    return new RoleBootstrapPlan(sourcePlan, assignments, counts(plan), plan.manifestHash());
  }

  /** 返回部署来源中的实际管理员工号。 */
  @Override
  public String initialAdminUsername() {
    return InitialAuthorizationPolicy.administrator(state()).username();
  }

  /** 加根锁后重新核验并写入；完整K检查和回执随首次发布一起提交。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BootstrapReport initialize(Path directory) {
    BootstrapPlan plan = plan(source.read(directory));
    maintenance.lock();
    Map<Long, Integer> assignments = validate(plan);
    if (assignments.isEmpty())
      return new BootstrapReport(counts(plan), 0, true, plan.manifestHash());
    List<BootstrapPlan.RoleSeed> roles =
        Arrays.stream(InitialRole.values())
            .map(
                role ->
                    InitialAuthorizationPolicy.role(
                        role.getCode(), role.getName(), role.getManagementLevel(), PERMISSIONS))
            .toList();
    initialization.publish(
        new BootstrapPlan.Publication(
            PERMISSIONS, roles, assignments, plan.manifestHash(), clock.instant()));
    maintenance.retainOne();
    return new BootstrapReport(counts(plan), assignments.size(), false, plan.manifestHash());
  }

  /** Repo只提供统计和关联事实，发布判断来自领域而非SQL存在性。 */
  private Map<Long, Integer> validate(BootstrapPlan plan) {
    try {
      return InitialAuthorizationPolicy.validate(plan, state(), initialization.catalog());
    } catch (InitializationViolation violation) {
      throw new BusinessException(409, violation.code(), violation.getMessage());
    }
  }

  /** 原始文件只在写入前读取一次，保持预览的摘要与执行计划一致。 */
  private BootstrapPlan plan(ImportPlan input) {
    return InitialAuthorizationPolicy.plan(DddImportManifest.from(input), PERMISSIONS, CODES);
  }

  /** 来源查询失败沿首次授权冲突契约。 */
  private InitializationState state() {
    try {
      return initialization.inspect();
    } catch (ImportException exception) {
      throw new BusinessException(409, "BOOTSTRAP_CONFLICT", "初始化来源无法核对");
    }
  }

  /** 汇总只暴露五角色人数，不包含人员清单。 */
  private Map<String, Long> counts(BootstrapPlan plan) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (InitialRole role : InitialRole.values()) {
      long count =
          role == InitialRole.SYSTEM_ADMIN
              ? 1
              : plan.employees().stream()
                  .filter(person -> person.level() == role.getManagementLevel())
                  .count();
      result.put(role.getName(), count);
    }
    return result;
  }
}
