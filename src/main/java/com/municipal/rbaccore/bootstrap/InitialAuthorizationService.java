package com.municipal.rbaccore.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.importing.ImportPlan;
import com.municipal.rbaccore.importing.ImportSource;
import com.municipal.rbaccore.importing.ImportSourceRepository;
import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import com.municipal.rbaccore.security.RbacCatalog;
import com.municipal.rbaccore.service.MaintenanceGuard;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 经确认规则的首次批量授权，预检后整批提交，回执存在时不恢复撤销或覆盖调整。 */
@Service
@RequiredArgsConstructor
public class InitialAuthorizationService {
  private final RoleBootstrapPlanner planner;
  private final BootstrapReceiptRepository receipts;
  private final ImportSourceRepository sources;
  private final DepartmentRepository departments;
  private final UserRepository users;
  private final RoleRepository roles;
  private final ApplicationSystemRepository systems;
  private final PermissionRepository permissions;
  private final ApiResourceRepository apis;
  private final MaintenanceGuard guard;
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
  public Report initialize(Path directory) {
    RoleBootstrapPlan plan = planner.plan(directory);
    guard.lock();
    try {
      validateSources(plan);
      Optional<BootstrapReceipt> previous = receipts.findById("rbac-initial-roles");
      if (previous.isPresent()) {
        if (!previous.get().getManifestSha256().equals(plan.manifestSha256())) {
          throw conflict("原件或初始化规则改变，不能自动重新授予");
        }
        return new Report(plan.plannedCounts(), 0, true, plan.manifestSha256());
      }
      requireEmptyCatalog();
      Map<String, User> accounts = validateAccounts(plan);
      Map<InitialRole, Role> initialRoles = createCatalog();
      for (RoleBootstrapPlan.Assignment assignment : plan.assignments()) {
        accounts.get(assignment.username()).assignRole(initialRoles.get(assignment.role()));
      }
      ImportSource adminSource = adminSource();
      User admin = accounts.get(adminSource.getSourceKey());
      if (admin == null
          || !admin.getId().equals(adminSource.getUser().getId())
          || !admin.getDepartment().getId().equals(rootSource().getDepartment().getId())) {
        throw conflict("独立管理员来源或归属不一致");
      }
      admin.assignRole(initialRoles.get(InitialRole.SYSTEM_ADMIN));
      // 回执必须在当前事务捕获约束并连同所有角色关联提交，不能先写完成标记。
      receipts.saveAndFlush(new BootstrapReceipt(plan.manifestSha256(), clock.instant()));
      guard.retainOne();
      return new Report(
          plan.plannedCounts(), plan.assignments().size() + 1, false, plan.manifestSha256());
    } catch (DataAccessException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "首次授权写入失败，整批已回滚");
    }
  }

  /** 核对原件摘要和源行；来源支撑记录损坏不能据其推导授权。 */
  private void validateSources(RoleBootstrapPlan plan) {
    List<ImportSource> records = sources.findAll();
    if (records.size() != 10112
        || departments.count() != 110
        || users.countBinaryLoginColumns() != 2) {
      throw conflict("T03完整导入或账号比较规则尚未就绪");
    }
    Map<String, ImportSource> indexed =
        records.stream()
            .collect(
                Collectors.toMap(
                    source -> source.getKind().name() + ":" + source.getSourceKey(),
                    Function.identity()));
    for (ImportPlan.Organization organization : plan.source().organizations()) {
      ImportSource source = indexed.get("ORGANIZATION:" + organization.name().trim());
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
      ImportSource source = indexed.get("EMPLOYEE:" + employee.username().trim());
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
    ImportSource root = rootSource();
    if (root.getDepartment().getParent() != null
        || departments.subtreeIds(root.getDepartment().getId()).size() != departments.count()) {
      throw conflict("共同根没有覆盖全部目标组织");
    }
    adminSource();
  }

  /** 初次分配必须对应T03导入的实际账号和部门，不能借手工移动先扩大范围。 */
  private Map<String, User> validateAccounts(RoleBootstrapPlan plan) {
    Map<String, User> accounts =
        users.findAllForBootstrap().stream()
            .collect(Collectors.toMap(User::getUsername, Function.identity()));
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
      User user = accounts.get(employee.username().trim());
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
  private Map<InitialRole, Role> createCatalog() {
    ApplicationSystem system =
        systems.save(new ApplicationSystem(RbacCatalog.SYSTEM_CODE, "RBAC权限管理系统"));
    Permission workspace =
        permissions.save(new Permission(system, "rbac.workspace", "权限管理", Permission.Type.MENU));
    Map<String, Permission> resourcePermissions = new LinkedHashMap<>();
    Map<RbacCatalog.Route, ApiResource> resourceApis = new HashMap<>();
    for (Map.Entry<String, List<RbacCatalog.Route>> definition :
        RbacCatalog.definitions().entrySet()) {
      Permission permission =
          new Permission(system, definition.getKey(), definition.getKey(), Permission.Type.BUTTON);
      permission.moveTo(workspace);
      for (RbacCatalog.Route route : definition.getValue()) {
        ApiResource api =
            resourceApis.computeIfAbsent(
                route, key -> apis.save(new ApiResource(system, key.method(), key.path())));
        permission.mapApi(api);
      }
      resourcePermissions.put(definition.getKey(), permissions.save(permission));
    }
    Map<InitialRole, Role> initialRoles = new EnumMap<>(InitialRole.class);
    for (InitialRole initial : InitialRole.values()) {
      Role role = new Role(initial.getCode(), initial.getName());
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
  private void requireSource(ImportSource source, String hash, int row, List<String> cells) {
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
  private ImportSource rootSource() {
    return sources
        .findByKindAndSourceKey(ImportSource.Kind.ORGANIZATION, "总公司")
        .orElseThrow(() -> conflict("共同根来源缺失"));
  }

  /** 独立初始化账号只能有一个，不把员工按姓名、岗位或角色名提升为全局管理员。 */
  private ImportSource adminSource() {
    List<ImportSource> admins = sources.findAllByKind(ImportSource.Kind.BOOTSTRAP);
    if (admins.size() != 1
        || !admins.getFirst().getUser().getUsername().equals(admins.getFirst().getSourceKey())) {
      throw conflict("独立管理员来源不唯一或不一致");
    }
    return admins.getFirst();
  }

  /** 预检失败使用统一冲突码，消息不打印完整人员资料。 */
  private static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "BOOTSTRAP_CONFLICT", message);
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

  /** 只输出规则人数及新建关联数量，不输出万人名单或任何口令。 */
  public record Report(
      Map<String, Long> plannedCounts,
      int createdAssignments,
      boolean alreadyInitialized,
      String manifestSha256) {}
}
