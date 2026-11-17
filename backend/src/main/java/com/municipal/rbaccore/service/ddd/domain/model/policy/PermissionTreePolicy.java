package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 权限树与API关联的约束，使用完整的当前节点事实判定。 */
public final class PermissionTreePolicy {
  private PermissionTreePolicy() {}

  /** 类型和父级同时校验，按钮无子节点，父子同系统，不能产生树环。 */
  public static Permission revise(
      Permission current,
      Map<Long, Permission> tree,
      Long parentId,
      Permission.Type type,
      Boolean enabled) {
    Permission.Type resultType = type == null ? current.type() : type;
    if (resultType == Permission.Type.BUTTON
        && tree.values().stream().anyMatch(node -> current.id().equals(node.parentId()))) {
      throw invalidTree();
    }
    if (parentId != null) {
      Permission parent = tree.get(parentId);
      if (parent == null
          || !Objects.equals(parent.system().id(), current.system().id())
          || parent.type() != Permission.Type.MENU) throw invalidTree();
      Set<Long> visited = new HashSet<>();
      for (Permission node = parent; node != null; node = tree.get(node.parentId())) {
        if (current.id().equals(node.id()) || !visited.add(node.id())) throw invalidTree();
      }
    }
    return current.revise(parentId, null, resultType, enabled);
  }

  /** 映射允许保留停用API，但批量ID必须完整且属于同一个系统。 */
  public static Permission map(Permission current, Set<ApiResource> resources, int requested) {
    if (resources.size() != requested
        || resources.stream().anyMatch(api -> !api.system().id().equals(current.system().id()))) {
      throw new DomainRuleViolation(DomainRuleViolation.Rule.INVALID_MAPPING, "API不存在或不属于当前系统");
    }
    return current.replaceApis(resources);
  }

  /** 树违反业务约束时给出统一的可翻译失败原因。 */
  private static DomainRuleViolation invalidTree() {
    return new DomainRuleViolation(DomainRuleViolation.Rule.INVALID_TREE, "权限父级或类型违反树约束");
  }
}
