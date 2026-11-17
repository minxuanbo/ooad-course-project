package com.municipal.rbaccore.service.structured.department;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.department.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.RequestContext;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.*;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化组织用例：根锁保护树、范围、人数和来源墓碑的一致性。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredDepartmentService implements DepartmentUseCase {
  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;
  private final StructuredAuthenticationService auth;
  private final StructuredDepartmentScopeService scope;
  private final StructuredPersonnelAuthority authority;
  private final StructuredEffectivePermissionService permissions;
  private final StructuredMaintenanceGuard guard;
  private final RequestContext requests;

  /** 先拒绝匿名、无API和越界请求，格式校验仍由DTO承担。 */
  public void checkTarget(String header, Long id, boolean write) {
    CurrentIdentity actor = requireOperation(header);
    if (write) requireManager(actor);
    if (id != null) {
      DepartmentEntity department = target(actor, id);
      if (write) requireEdit(actor, department);
    }
  }

  /** 先限定范围，再一次聚合直属人数，递归累加直接子树，避免重复计数。 */
  public List<DepartmentResult> list(String header) {
    CurrentIdentity actor = auth.require(header);
    List<Long> ids = scope.allowedIds(actor);
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : users.departmentCounts(ids)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    NodeCapabilities capabilities = capabilities(actor);
    Map<Long, DepartmentResult> indexed = new LinkedHashMap<>();
    for (DepartmentEntity department : departments.findAllById(ids)) {
      DepartmentResult result = result(actor, department, capabilities);
      result.setDirectUsers(counts.getOrDefault(department.getId(), 0L));
      indexed.put(result.getId(), result);
    }
    List<DepartmentResult> ordered = new ArrayList<>();
    for (DepartmentResult root :
        indexed.values().stream()
            .filter(item -> item.getParentId() == null)
            .sorted(Comparator.comparing(DepartmentResult::getId))
            .toList()) {
      append(root, indexed, ordered);
    }
    return ordered;
  }

  /** 本人或后代可作为父级，类型及可信总公司身份在锁内独立验证。 */
  @Transactional
  public DepartmentResult create(String header, CreateDepartmentCommand command) {
    return write(
        header,
        actor -> {
          DepartmentEntity parent = target(actor, command.getParentId());
          requireManager(actor);
          if (!allowsChild(parent.getType(), command.getType())
              || (parent.getType() == DepartmentType.HEADQUARTERS
                  && !parent.getId().equals(scope.trustedRootId()))) {
            throw BusinessException.bad("DEPARTMENT_TYPE_INVALID", "该层级不允许新增此类下级");
          }
          requireUnique(parent.getId(), command.getName(), null);
          DepartmentEntity department =
              new DepartmentEntity("org-" + UUID.randomUUID(), command.getName());
          CloneFactory.copyNotNull(department, command);
          department.moveTo(parent);
          return result(actor, departments.saveAndFlush(department));
        });
  }

  /** 结构化组织层级规则不放进公共契约；后续DDD由领域对象独立表达同一业务要求。 */
  private boolean allowsChild(DepartmentType parent, DepartmentType child) {
    return switch (parent) {
      case HEADQUARTERS ->
          child == DepartmentType.COMPANY
              || child == DepartmentType.DEPARTMENT
              || child == DepartmentType.PROJECT;
      case COMPANY -> child == DepartmentType.DEPARTMENT || child == DepartmentType.PROJECT;
      case DEPARTMENT -> child == DepartmentType.PROJECT;
      case PROJECT -> false;
    };
  }

  /** 局部管理者只能修改严格下级；改名不改变可信根来源或原始Excel。 */
  @Transactional
  public DepartmentResult rename(String header, Long id, RenameDepartmentCommand command) {
    return write(
        header,
        actor -> {
          DepartmentEntity department = target(actor, id);
          requireEdit(actor, department);
          requireUnique(
              department.getParent() == null ? null : department.getParent().getId(),
              command.getName(),
              id);
          CloneFactory.copyNotNull(department, command);
          return result(actor, departments.saveAndFlush(department));
        });
  }

  /** 空节点软删除保留导入来源，不再出现在查询、候选和范围中。 */
  @Transactional
  public void delete(String header, Long id) {
    write(
        header,
        actor -> {
          DepartmentEntity department = target(actor, id);
          if (id.equals(scope.trustedRootId())) {
            throw new BusinessException(409, "DEPARTMENT_ROOT_REQUIRED", "总公司根节点不能删除");
          }
          requireEdit(actor, department);
          if (users.existsByDepartmentId(id) || departments.existsByParentIdAndDeletedFalse(id)) {
            throw new BusinessException(409, "DEPARTMENT_NOT_EMPTY", "部门有人员或下级，不能删除");
          }
          department.setDeleted(true);
          departments.saveAndFlush(department);
          return null;
        });
  }

  /** 同父名称按数据库二进制规则去空白判重；索引仍兜底并发冲突。 */
  private void requireUnique(Long parent, String name, Long excluding) {
    if (departments.siblingNameExists(parent, name.trim(), excluding)) {
      throw new BusinessException(409, "DEPARTMENT_NAME_CONFLICT", "同一上级下部门名称不能重复");
    }
  }

  /** 严格后代规则与管理员元数据均不能仅靠前端按钮判断。 */
  private void requireEdit(CurrentIdentity actor, DepartmentEntity department) {
    requireManager(actor);
    if (department.getId().equals(actor.departmentId())
        && !(authority.level(actor) == 5 && scope.isGlobal(actor))) {
      throw BusinessException.scopeDenied();
    }
  }

  /** 获得单个部门API仍不能使普通员工绕过管理级别边界。 */
  private void requireManager(CurrentIdentity actor) {
    if (authority.level(actor) < 2) throw BusinessException.scopeDenied();
  }

  /** 已删除或范围外节点不暴露详情，共同根范围下未知ID返回404。 */
  private DepartmentEntity target(CurrentIdentity actor, Long id) {
    if (!scope.allowedIds(actor).contains(id)) {
      if (scope.isGlobal(actor)) throw new BusinessException(404, "RESOURCE_NOT_FOUND", "部门不存在");
      throw BusinessException.scopeDenied();
    }
    return departments
        .findById(id)
        .filter(item -> !item.isDeleted())
        .orElseThrow(BusinessException::scopeDenied);
  }

  /** 所有树写与人员调动共用根锁，锁后重新鉴权，不采信旧身份或权限。 */
  private <T> T write(String header, Function<CurrentIdentity, T> change) {
    try {
      guard.lock();
      CurrentIdentity actor = requireOperation(header);
      T result = change.apply(actor);
      guard.retainOne();
      return result;
    } catch (DataAccessException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "部门保存冲突，请刷新后重试");
    }
  }

  /** 根据本请求真实方法和Spring模板判定，不接收前端权限结论。 */
  private CurrentIdentity requireOperation(String header) {
    CurrentIdentity actor = auth.require(header);
    RequestContext.Operation operation = requests.operation();
    if (operation == null
        || !permissions.allows(actor.id(), operation.method(), operation.template())) {
      throw BusinessException.forbidden();
    }
    return actor;
  }

  /** 返回节点只读属性及当前操作能力，API授权仍在每次写时检查。 */
  private DepartmentResult result(CurrentIdentity actor, DepartmentEntity department) {
    return result(actor, department, capabilities(actor));
  }

  /** 目录查询只计算一次范围和实际路由，避免每个部门重复读取权限链。 */
  private NodeCapabilities capabilities(CurrentIdentity actor) {
    int level = authority.level(actor);
    Set<com.municipal.rbaccore.service.port.security.RbacCatalog.Route> routes =
        permissions.routes(actor.id());
    return new NodeCapabilities(
        level >= 2,
        level == 5 && scope.isGlobal(actor),
        routes.contains(
            new com.municipal.rbaccore.service.port.security.RbacCatalog.Route(
                "POST", "/api/departments")),
        routes.contains(
            new com.municipal.rbaccore.service.port.security.RbacCatalog.Route(
                "PATCH", "/api/departments/{id}")),
        routes.contains(
            new com.municipal.rbaccore.service.port.security.RbacCatalog.Route(
                "DELETE", "/api/departments/{id}")),
        scope.trustedRootId());
  }

  /** 共同字段用CloneFactory，关系及权限派生属性显式补充。 */
  private DepartmentResult result(
      CurrentIdentity actor, DepartmentEntity department, NodeCapabilities capabilities) {
    DepartmentResult result = CloneFactory.copy(new DepartmentResult(), department);
    result.setParentId(
        department.getId().equals(actor.departmentId()) || department.getParent() == null
            ? null
            : department.getParent().getId());
    boolean edit =
        capabilities.manager()
            && (!department.getId().equals(actor.departmentId()) || capabilities.administrator());
    result.setCanCreate(
        capabilities.manager()
            && department.getType() != DepartmentType.PROJECT
            && capabilities.create());
    result.setCanEdit(edit && capabilities.edit());
    result.setCanDelete(
        edit && !department.getId().equals(capabilities.rootId()) && capabilities.delete());
    return result;
  }

  /** 仅当前请求内复用，不能作为下一请求的授权依据。 */
  private record NodeCapabilities(
      boolean manager,
      boolean administrator,
      boolean create,
      boolean edit,
      boolean delete,
      Long rootId) {}

  /** 深度优先输出连续子树，总人数只加直属人数和直接子节点总数。 */
  private long append(
      DepartmentResult node, Map<Long, DepartmentResult> indexed, List<DepartmentResult> ordered) {
    ordered.add(node);
    long total = node.getDirectUsers();
    for (DepartmentResult child :
        indexed.values().stream()
            .filter(item -> node.getId().equals(item.getParentId()))
            .sorted(Comparator.comparing(DepartmentResult::getId))
            .toList()) {
      total += append(child, indexed, ordered);
    }
    node.setTotalUsers(total);
    if (total > 0
        || indexed.values().stream().anyMatch(item -> node.getId().equals(item.getParentId()))) {
      node.setCanDelete(false);
    }
    return total;
  }
}
