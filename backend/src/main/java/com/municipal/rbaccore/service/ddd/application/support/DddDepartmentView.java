package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.department.DepartmentType;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.port.security.RbacCatalog.Route;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 组织用例共用查询结果装配，范围及规则依赖独立领域事实。 */
@Component
@RequiredArgsConstructor
public class DddDepartmentView {
  private final UserRepository users;
  private final ManagementAuthorization authorization;

  /** 一次查询投影完整可见树与人数，根的外部父关系不返回。 */
  public View build(User actor, OrganizationFacts facts) {
    Long own = actor.department().id();
    OrganizationFacts.Scope scope = OrganizationPolicy.scope(facts, own);
    OrganizationFacts.Statistics statistics = users.statistics(scope);
    Map<Long, Long> counts =
        statistics.directCounts().stream()
            .collect(
                Collectors.toMap(
                    OrganizationFacts.Count::departmentId, OrganizationFacts.Count::directUsers));
    Map<Long, List<Department>> children = new HashMap<>();
    Department root = null;
    for (Department department :
        facts.departments().stream().sorted(Comparator.comparing(Department::id)).toList()) {
      if (!scope.departmentIds().contains(department.id())) continue;
      if (department.id().equals(own)) root = department;
      else
        children.computeIfAbsent(department.parentId(), key -> new ArrayList<>()).add(department);
    }
    if (root == null) throw BusinessException.scopeDenied();
    Set<Route> routes = authorization.routes(actor.id());
    Capabilities capabilities =
        new Capabilities(
            actor.level() >= Role.FIRST_MANAGER_LEVEL,
            actor.level() == Role.ADMIN_LEVEL && OrganizationPolicy.global(facts, own),
            routes.contains(new Route("POST", "/api/departments")),
            routes.contains(new Route("PATCH", "/api/departments/{id}")),
            routes.contains(new Route("DELETE", "/api/departments/{id}")),
            facts.trustedRootId(),
            facts.roleOwnerIds());
    List<DepartmentResult> tree = new ArrayList<>();
    append(root, null, children, counts, capabilities, tree);
    return new View(tree, statistics.enabled());
  }

  /** 先输出父节点，再递归各直接下级，同时累计各自人数。 */
  private long append(
      Department department,
      Long parent,
      Map<Long, List<Department>> children,
      Map<Long, Long> counts,
      Capabilities capabilities,
      List<DepartmentResult> result) {
    List<Department> descendants = children.getOrDefault(department.id(), List.of());
    long direct = counts.getOrDefault(department.id(), 0L);
    DepartmentResult node = new DepartmentResult();
    node.setId(department.id());
    node.setParentId(parent);
    node.setName(department.name());
    node.setType(DepartmentType.valueOf(department.type().name()));
    node.setDirectUsers(direct);
    boolean editable = capabilities.manager() && (parent != null || capabilities.administrator());
    node.setCanCreate(
        capabilities.manager()
            && capabilities.create()
            && department.type() != Department.Type.PROJECT
            && (department.type() != Department.Type.HEADQUARTERS
                || department.id().equals(capabilities.root())));
    node.setCanEdit(editable && capabilities.edit());
    node.setCanDelete(
        editable
            && capabilities.delete()
            && !department.id().equals(capabilities.root())
            && direct == 0
            && descendants.isEmpty()
            && !capabilities.roleOwners().contains(department.id()));
    result.add(node);
    long total = direct;
    for (Department child : descendants) {
      total += append(child, department.id(), children, counts, capabilities, result);
    }
    node.setTotalUsers(total);
    return total;
  }

  /** 同一快照读取的部门列表及启用人数。 */
  public record View(List<DepartmentResult> tree, long enabled) {}

  /** 本次查询的可见操作能力，用于生成按钮提示。 */
  private record Capabilities(
      boolean manager,
      boolean administrator,
      boolean create,
      boolean edit,
      boolean delete,
      Long root,
      Set<Long> roleOwners) {}
}
