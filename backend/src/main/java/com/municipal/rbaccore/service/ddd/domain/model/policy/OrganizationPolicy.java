package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 根据独立来源和当前有效部门关系计算范围及允许的组织层级。 */
public final class OrganizationPolicy {
  private OrganizationPolicy() {}

  /** 可信来源根必须保留，删除前先核查这一不变量。 */
  public static void requireNonRoot(Department node, OrganizationFacts facts) {
    if (Objects.equals(node.id(), facts.trustedRootId())) {
      throw new DomainRuleViolation(
          DomainRuleViolation.Rule.DEPARTMENT_ROOT_REQUIRED, "总公司根节点不能删除");
    }
  }

  /** 已核实不存在人员和下级时才允许生成来源墓碑。 */
  public static void requireEmpty(OrganizationFacts.Usage usage) {
    if (usage.hasUsers() || usage.hasChildren()) {
      throw new DomainRuleViolation(
          DomainRuleViolation.Rule.DEPARTMENT_NOT_EMPTY, "部门有人员或下级，不能删除");
    }
  }

  /** 同父有效部门名称唯一，改名时排除当前节点。 */
  public static void requireUnique(
      OrganizationFacts facts, Long parent, String name, Long existing) {
    if (facts.departments().stream()
        .anyMatch(node -> Objects.equals(parent, node.parentId())
            && !node.id().equals(existing) && node.name().equals(name.trim()))) {
      throw new DomainRuleViolation(
          DomainRuleViolation.Rule.DEPARTMENT_NAME_CONFLICT, "同一上级下部门名称不能重复");
    }
  }

  /** 从指定节点遍历后代，墓碑不进入事实集，已访问集合避免损坏数据造成死循环。 */
  public static OrganizationFacts.Scope scope(OrganizationFacts facts, Long root) {
    if (facts.departments().stream().noneMatch(node -> node.id().equals(root))) {
      return new OrganizationFacts.Scope(Set.of());
    }
    Map<Long, List<Department>> children = new HashMap<>();
    for (Department node : facts.departments()) {
      if (node.parentId() != null) {
        children.computeIfAbsent(node.parentId(), key -> new java.util.ArrayList<>()).add(node);
      }
    }
    ArrayDeque<Long> pending = new ArrayDeque<>();
    LinkedHashSet<Long> visited = new LinkedHashSet<>();
    pending.add(root);
    while (!pending.isEmpty()) {
      Long next = pending.removeFirst();
      if (visited.add(next)) {
        children.getOrDefault(next, List.of()).forEach(child -> pending.addLast(child.id()));
      }
    }
    return new OrganizationFacts.Scope(visited);
  }

  /** 只有可覆盖整棵有效树的来源根直属人员具备全局范围。 */
  public static boolean global(OrganizationFacts facts, Long ownDepartment) {
    return Objects.equals(facts.trustedRootId(), ownDepartment)
        && facts.departments().stream()
            .anyMatch(node -> node.id().equals(ownDepartment) && node.parentId() == null)
        && scope(facts, ownDepartment).departmentIds().size() == facts.departments().size();
  }

  /** 总公司必须由原始资料确认；项目节点不再接受下级。 */
  public static boolean allowsChild(Department parent, Department.Type child, Long trustedRoot) {
    return switch (parent.type()) {
      case HEADQUARTERS -> parent.id().equals(trustedRoot) && child != Department.Type.HEADQUARTERS;
      case COMPANY -> child == Department.Type.DEPARTMENT || child == Department.Type.PROJECT;
      case DEPARTMENT -> child == Department.Type.PROJECT;
      case PROJECT -> false;
    };
  }
}
