package com.municipal.rbaccore.service.structured.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.BootstrapReceiptEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ImportSourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApiResourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.BootstrapReceiptJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ImportSourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.bootstrap.AuthorizationBootstrapUseCase;
import com.municipal.rbaccore.service.port.bootstrap.BootstrapReport;
import com.municipal.rbaccore.service.port.bootstrap.InitialRole;
import com.municipal.rbaccore.service.port.bootstrap.RoleBootstrapPlan;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 经确认规则的首次批量授权，预检后整批提交，回执存在时不恢复撤销或覆盖调整。 */
@Service
@RequiredArgsConstructor
public class StructuredInitialAuthorizationService implements AuthorizationBootstrapUseCase {
  private final RoleBootstrapPlanner planner;
  private final BootstrapReceiptJpaRepository receipts;
  private final ImportSourceJpaRepository sources;
  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;
  private final RoleJpaRepository roles;
  private final ApplicationSystemJpaRepository systems;
  private final PermissionJpaRepository permissions;
  private final ApiResourceJpaRepository apis;
  private final StructuredMaintenanceGuard guard;
  private final Clock clock;
  private final ObjectMapper mapper;

  /** 预检只生成可核对清单，不改变角色、口令、状态、部门或权限。 */
  @Transactional(readOnly = true)
  public RoleBootstrapPlan preview(Path directory) {
    RoleBootstrapPlan plan = planner.plan(directory);
    validateSources(plan);
    if (!receipts.existsById("rbac-initial-roles")) {
      requireEmptyCatalog();
      validateAccounts(plan);
    }
    return plan;
  }

  /** 预检清单中的独立管理员使用T03来源账号名，支持部署时已确认的自定义名。 */
  @Transactional(readOnly = true)
  public String initialAdminUsername() {
    return adminSource().getSourceKey();
  }

  /** 角色、资源、万人关联和完成回执同一事务，任何失败均安全回滚重试。 */
  @Transactional
  public BootstrapReport initialize(Path directory) {
    RoleBootstrapPlan plan = planner.plan(directory);
    guard.lock();
    try {
      validateSources(plan);
      Optional<BootstrapReceiptEntity> previous = receipts.findById("rbac-initial-roles");
      if (previous.isPresent()) {
        if (!previous.get().getManifestSha256().equals(plan.manifestSha256())) {
          throw conflict("原件或初始化规则改变，不能自动重新授予");
        }
        return new BootstrapReport(plan.plannedCounts(), 0, true, plan.manifestSha256());
      }
      requireEmptyCatalog();
      Map<String, UserEntity> accounts = validateAccounts(plan);
      Map<InitialRole, RoleEntity> initialRoles = createCatalog();
      for (RoleBootstrapPlan.Assignment assignment : plan.assignments()) {
        accounts.get(assignment.username()).assignRole(initialRoles.get(assignment.role()));
      }
      ImportSourceEntity adminSource = adminSource();
      UserEntity admin = accounts.get(adminSource.getSourceKey());
      if (admin == null
          || !admin.getId().equals(adminSource.getUser().getId())
          || !admin.getDepartment().getId().equals(rootSource().getDepartment().getId())) {
        throw conflict("独立管理员来源或归属不一致");
      }
      admin.assignRole(initialRoles.get(InitialRole.SYSTEM_ADMIN));
      // 回执必须在当前事务捕获约束并连同所有角色关联提交，不能先写完成标记。
      receipts.saveAndFlush(new BootstrapReceiptEntity(plan.manifestSha256(), clock.instant()));
      guard.retainOne();
      return new BootstrapReport(
          plan.plannedCounts(), plan.assignments().size() + 1, false, plan.manifestSha256());
    } catch (DataAccessException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "首次授权写入失败，整批已回滚");
    }
  }

  /** 核对原件摘要和源行；来源支撑记录损坏不能据其推导授权。 */
  private void validateSources(RoleBootstrapPlan plan) {
    List<ImportSourceEntity> records = sources.findAll();
    if (records.size() != 10112 || users.countBinaryLoginColumns() != 2) {
      throw conflict("T03完整导入或账号比较规则尚未就绪");
    }
    Map<String, ImportSourceEntity> indexed =
        records.stream()
            .collect(
                Collectors.toMap(
                    source -> source.getKind().name() + ":" + source.getSourceKey(),
                    Function.identity()));
    for (ImportPlan.Organization organization : plan.source().organizations()) {
      ImportSourceEntity source = indexed.get("ORGANIZATION:" + organization.name().trim());
      requireSource(
          source,
          plan.source().organizationSha256(),
          organization.row(),
          List.of(organization.name(), organization.manager(), organization.parent()));
      if (!source.getDepartment().getCode().equals(organization.targetCode())) {
        throw conflict("组织来源目标不一致");
      }
    }
    for (ImportPlan.Employee employee : plan.source().employees()) {
      ImportSourceEntity source = indexed.get("EMPLOYEE:" + employee.username().trim());
      requireSource(
          source,
          plan.source().employeeSha256(),
          employee.row(),
          List.of(
              employee.name(),
              employee.username(),
              employee.company(),
              employee.department(),
              employee.position(),
              employee.phone()));
      if (!source.getUser().getUsername().equals(employee.username().trim())) {
        throw conflict("工号与来源用户不一致");
      }
    }
    ImportSourceEntity root = rootSource();
    if (root.getDepartment().getParent() != null
        || departments.subtreeIds(root.getDepartment().getId()).size()
            != departments.countByDeletedFalse()) {
      throw conflict("共同根没有覆盖全部目标组织");
    }
    adminSource();
  }

  /** 初次分配必须对应T03导入的实际账号和部门，不能借手工移动先扩大范围。 */
  private Map<String, UserEntity> validateAccounts(RoleBootstrapPlan plan) {
    Map<String, UserEntity> accounts =
        users.findAllForBootstrap().stream()
            .collect(Collectors.toMap(UserEntity::getUsername, Function.identity()));
    if (accounts.size() != 10001) {
      throw conflict("首次授权要求完整的10000名员工及独立管理员");
    }
    Map<String, String> departmentCodes =
        plan.source().organizations().stream()
            .collect(
                Collectors.toMap(
                    ImportPlan.Organization::targetName,
                    ImportPlan.Organization::targetCode,
                    (left, right) -> left));
    for (ImportPlan.Employee employee : plan.source().employees()) {
      UserEntity user = accounts.get(employee.username().trim());
      if (user == null
          || !user.getDepartment()
              .getCode()
              .equals(departmentCodes.get(employee.targetDepartment()))
          || !user.getRoles().isEmpty()) {
        throw conflict("来源第" + employee.row() + "行人员缺失、归属改变或已有角色，拒绝自动覆盖");
      }
    }
    return accounts;
  }

  /** 同一份RBAC自身目录供五角色引用，Excel其他系统目录不整包迁入。 */
  private Map<InitialRole, RoleEntity> createCatalog() {
    ApplicationSystemEntity system =
        systems.save(new ApplicationSystemEntity(RbacCatalog.SYSTEM_CODE, "RBAC权限管理系统"));
    PermissionEntity workspace =
        permissions.save(
            new PermissionEntity(system, "rbac.workspace", "权限管理", PermissionType.MENU));
    Map<String, PermissionEntity> resourcePermissions = new LinkedHashMap<>();
    Map<RbacCatalog.Route, ApiResourceEntity> resourceApis = new HashMap<>();
    for (Map.Entry<String, List<RbacCatalog.Route>> definition :
        RbacCatalog.definitions().entrySet()) {
      PermissionEntity permission =
          new PermissionEntity(
              system, definition.getKey(), definition.getKey(), PermissionType.BUTTON);
      permission.moveTo(workspace);
      for (RbacCatalog.Route route : definition.getValue()) {
        ApiResourceEntity api =
            resourceApis.computeIfAbsent(
                route, key -> apis.save(new ApiResourceEntity(system, key.method(), key.path())));
        permission.mapApi(api);
      }
      resourcePermissions.put(definition.getKey(), permissions.save(permission));
    }
    Map<InitialRole, RoleEntity> initialRoles = new EnumMap<>(InitialRole.class);
    for (InitialRole initial : InitialRole.values()) {
      RoleEntity role = new RoleEntity(initial.getCode(), initial.getName());
      role.setManagementLevel(initial.getManagementLevel());
      if (initial != InitialRole.EMPLOYEE) {
        role.grantPermission(workspace);
        resourcePermissions.forEach(
            (code, permission) -> {
              if (initial == InitialRole.SYSTEM_ADMIN || RbacCatalog.PERSONNEL.contains(code)) {
                role.grantPermission(permission);
              }
            });
      }
      initialRoles.put(initial, roles.save(role));
    }
    return initialRoles;
  }

  /** 来源摘要与原始列同时匹配，不能只核对行数。 */
  private void requireSource(ImportSourceEntity source, String hash, int row, List<String> cells) {
    try {
      if (source == null
          || !hash.equals(source.getFileSha256())
          || !Objects.equals(source.getSourceRow(), row)
          || !mapper.writeValueAsString(cells).equals(source.getSourceJson())) {
        throw conflict("原件或来源内容不一致");
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw conflict("来源内容无法核对");
    }
  }

  /** 根来自T03固定来源，不使用角色名称或任何空parent作为授权条件。 */
  private ImportSourceEntity rootSource() {
    return sources
        .findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司")
        .orElseThrow(() -> conflict("共同根来源缺失"));
  }

  /** 独立初始化账号只能有一个，不把员工按姓名、岗位或角色名提升为全局管理员。 */
  private ImportSourceEntity adminSource() {
    List<ImportSourceEntity> admins = sources.findAllByKind(ImportSourceEntity.Kind.BOOTSTRAP);
    if (admins.size() != 1
        || !admins.getFirst().getUser().getUsername().equals(admins.getFirst().getSourceKey())) {
      throw conflict("独立管理员来源不唯一或不一致");
    }
    return admins.getFirst();
  }

  /** 预检失败使用统一冲突码，消息不打印完整人员资料。 */
  private static BusinessException conflict(String message) {
    return new BusinessException(409, "BOOTSTRAP_CONFLICT", message);
  }

  /** 预检和正式执行使用同一冲突条件，不自动认领缺少回执的授权数据。 */
  private void requireEmptyCatalog() {
    if (roles.count() != 0
        || systems.count() != 0
        || permissions.count() != 0
        || apis.count() != 0) {
      throw conflict("已有授权数据但缺少完成回执，拒绝认领或覆盖");
    }
  }
}
