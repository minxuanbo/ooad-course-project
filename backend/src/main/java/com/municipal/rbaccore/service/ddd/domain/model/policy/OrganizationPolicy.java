package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.Department;
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
