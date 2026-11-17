package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** 首次五角色分类与核验，按已确认原件规则构造一次性的发布计划。 */
public final class InitialAuthorizationPolicy {
  private static final int COMPANY_LEADER = 4;
  private static final int DEPARTMENT_MANAGER = 3;
  private static final int PROJECT_MANAGER = 2;
  private static final int SOURCE_ORGANIZATIONS = 111;
  private static final int SOURCE_EMPLOYEES = 10000;

  private InitialAuthorizationPolicy() {}

  /** 从原表逐人分配角色，用固定序列与结构化回执兼容。 */
  public static BootstrapPlan plan(
      ImportManifest source,
      List<BootstrapPlan.PermissionSeed> permissions,
      Map<Integer, String> codes) {
    List<BootstrapPlan.Assignment> assignments =
        source.people().stream()
            .map(
                person ->
                    new BootstrapPlan.Assignment(
                        person.username().trim(), person.row(), rank(person)))
            .toList();
    StringBuilder digest = new StringBuilder(source.organizationHash()).append(source.peopleHash());
    assignments.forEach(
        item ->
            digest
                .append('\n')
                .append(item.username())
                .append(':')
                .append(codes.get(item.level())));
    permissions.forEach(
        permission ->
            permission
                .routes()
                .forEach(
                    route ->
                        digest
                            .append('\n')
                            .append(permission.code())
                            .append(':')
                            .append(route.method())
                            .append(':')
                            .append(route.path())));
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256")
              .digest(digest.toString().getBytes(StandardCharsets.UTF_8));
      return new BootstrapPlan(source, assignments, HexFormat.of().formatHex(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("运行环境缺少SHA-256", exception);
    }
  }

  /** 旧回执只做核对；首次发布还要验证账户原归属和无遗留角色。 */
  public static Map<Long, Integer> validate(
      BootstrapPlan plan, InitializationState state, BootstrapPlan.Catalog catalog) {
    if (plan.source().organizations().size() != SOURCE_ORGANIZATIONS
        || plan.employees().size() != SOURCE_EMPLOYEES) throw conflict("T03完整导入尚未就绪");
    ImportPolicy.Index stored;
    try {
      stored = ImportPolicy.inspect(plan.source(), state);
    } catch (InitializationViolation invalid) {
      throw conflict(invalid.getMessage());
    }
    InitializationState.Source rootSource =
        stored.source(InitializationState.Kind.ORGANIZATION, ImportManifest.ROOT_NAME);
    Long root = rootSource.departmentId();
    OrganizationFacts facts =
        new OrganizationFacts(
            root, state.departments().stream().filter(node -> !node.deleted()).toList());
    if (!OrganizationPolicy.global(facts, root)) throw conflict("共同根没有覆盖全部目标组织");
    InitializationState.Account admin = administrator(state);
    if (catalog.receiptHash() != null) {
      if (!catalog.receiptHash().equals(plan.manifestHash())) throw conflict("原件或初始化规则改变");
      return Map.of();
    }
    if (!catalog.empty()) throw conflict("已有授权数据但缺少完成回执");
    if (stored.accounts().size() != SOURCE_EMPLOYEES + 1
        || !root.equals(admin.departmentId())
        || admin.roleCount() != 0) throw conflict("首次授权要求来源账号完整且维护账号直属可信根");

    Map<String, String> codes = ImportPolicy.targetCodes(plan.source());
    Map<Long, Integer> assignments = new LinkedHashMap<>();
    for (int index = 0; index < plan.employees().size(); index++) {
      BootstrapPlan.Assignment assignment = plan.employees().get(index);
      ImportManifest.Person original = plan.source().people().get(index);
      InitializationState.Account user = stored.accounts().get(assignment.username());
      if (user == null
          || user.roleCount() != 0
          || !Objects.equals(user.departmentCode(), codes.get(original.target()))) {
        throw conflict("来源第" + assignment.sourceRow() + "行人员缺失、归属改变或已有角色");
      }
      assignments.put(user.id(), assignment.level());
    }
    assignments.put(admin.id(), Role.ADMIN_LEVEL);
    return assignments;
  }

  /** 部署账号由独立来源记录定位，与员工角色分类无关。 */
  public static InitializationState.Account administrator(InitializationState state) {
    List<InitializationState.Source> sources =
        state.sources().stream()
            .filter(source -> source.kind() == InitializationState.Kind.BOOTSTRAP)
            .toList();
    if (sources.size() != 1) throw conflict("独立管理员来源不唯一或缺失");
    InitializationState.Source binding = sources.getFirst();
    return state.accounts().stream()
        .filter(user -> user.id().equals(binding.userId()) && user.username().equals(binding.key()))
        .findFirst()
        .orElseThrow(() -> conflict("独立管理员来源不一致"));
  }

  /** 分配直接授权，三个管理等级只取得本RBAC人员和部门操作。 */
  public static BootstrapPlan.RoleSeed role(
      String code, String name, int level, List<BootstrapPlan.PermissionSeed> permissions) {
    Set<String> selected = new LinkedHashSet<>();
    if (level > Role.DEFAULT_LEVEL) {
      selected.add(BootstrapPlan.WORKSPACE_CODE);
      for (BootstrapPlan.PermissionSeed permission : permissions) {
        if (level == Role.ADMIN_LEVEL || permission.scopedManagers())
          selected.add(permission.code());
      }
    }
    return new BootstrapPlan.RoleSeed(code, name, level, selected);
  }

  /** 只匹配核实的精确岗位与原组织，未知岗位保持普通员工。 */
  private static int rank(ImportManifest.Person employee) {
    String company = employee.company().trim();
    String department = employee.department().trim();
    if (company.equals(department)
        || (ImportManifest.ROOT_NAME.equals(company)
            && ImportManifest.MERGED_LEADERS.equals(department))) return COMPANY_LEADER;
    return switch (employee.position().trim()) {
      case "部长", "副部长" -> DEPARTMENT_MANAGER;
      case "项目经理", "项目副经理" -> PROJECT_MANAGER;
      default -> Role.DEFAULT_LEVEL;
    };
  }

  /** 离线冲突不冒充HTTP目录错误枚举。 */
  private static InitializationViolation conflict(String message) {
    return new InitializationViolation("BOOTSTRAP_CONFLICT", message);
  }
}
