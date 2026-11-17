package com.municipal.rbaccore.service.ddd.application.department;

import com.municipal.rbaccore.service.ddd.application.support.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.department.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 部门范围查询、新增合法下级、改名及空节点墓碑。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddDepartmentService implements DepartmentUseCase {
  private final AuthenticationUseCase authentication;
  private final DddRequestAccess access;
  private final DddWrite writes;
  private final DddScope scope;
  private final DddDepartmentView view;
  private final OrganizationRepository departments;
  private final UserRepository users;

  /** 解析请求前核实实际操作权限和部门目标。 */
  @Override
  public void checkTarget(String header, Long id, boolean write) {
    User actor = person(access.require(header));
    if (write) requireManager(actor);
    if (id != null) {
      OrganizationFacts facts = scope.facts();
      Department node = target(actor, facts, id);
      if (write) requireEdit(actor, facts, node);
    }
  }

  /** 按连续子树返回范围及直接与累计人数。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<DepartmentResult> list(String header) {
    return view.build(person(authentication.require(header)), scope.facts()).tree();
  }

  /** 祖先范围和合法父子类型在拿到根锁后再判断。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DepartmentResult create(String header, CreateDepartmentCommand command) {
    return writes.scoped(
        header,
        identity -> {
          User actor = person(identity);
          OrganizationFacts facts = scope.facts();
          Department parent = target(actor, facts, command.getParentId());
          requireManager(actor);
          Department.Type child = Department.Type.valueOf(command.getType().name());
          if (!OrganizationPolicy.allowsChild(parent, child, facts.trustedRootId())) {
            throw BusinessException.bad("DEPARTMENT_TYPE_INVALID", "该层级不允许新增此类下级");
          }
          requireUnique(facts, parent.id(), command.getName(), null);
          Department saved =
              departments.save(
                  Department.child(
                      "org-" + UUID.randomUUID(), command.getName(), parent.id(), child));
          return savedView(actor, saved.id());
        });
  }

  /** 改名保留既有组织身份并返回真实人数和当前删除能力。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DepartmentResult rename(String header, Long id, RenameDepartmentCommand command) {
    return writes.scoped(
        header,
        identity -> {
          User actor = person(identity);
          OrganizationFacts facts = scope.facts();
          Department node = target(actor, facts, id);
          requireEdit(actor, facts, node);
          requireUnique(facts, node.parentId(), command.getName(), id);
          return savedView(actor, departments.save(node.rename(command.getName())).id());
        });
  }

  /** 空且非可信根的部门才可标记删除，原来源外键保留。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(String header, Long id) {
    writes.scoped(
        header,
        identity -> {
          User actor = person(identity);
          OrganizationFacts facts = scope.facts();
          Department node = target(actor, facts, id);
          if (id.equals(facts.trustedRootId())) {
            throw new BusinessException(409, "DEPARTMENT_ROOT_REQUIRED", "总公司根节点不能删除");
          }
          requireEdit(actor, facts, node);
          OrganizationFacts.Usage usage = departments.usage(new Department.Key(id));
          if (usage.hasUsers() || usage.hasChildren()) {
            throw new BusinessException(409, "DEPARTMENT_NOT_EMPTY", "部门有人员或下级，不能删除");
          }
          departments.save(node.tombstone());
          return null;
        });
  }

  /** 读取当前有效人员以及固定角色级别。 */
  private User person(CurrentIdentity actor) {
    return users.find(new User.Key(actor.id())).orElseThrow(BusinessException::unauthorized);
  }

  /** 基础管理级别和当前范围分别检查。 */
  private void requireManager(User actor) {
    if (actor.level() < Role.FIRST_MANAGER_LEVEL) throw BusinessException.scopeDenied();
  }

  /** 局部管理员不能改本人直属部门。 */
  private void requireEdit(User actor, OrganizationFacts facts, Department node) {
    requireManager(actor);
    if (node.id().equals(actor.department().id())
        && !(actor.level() == Role.ADMIN_LEVEL
            && OrganizationPolicy.global(facts, actor.department().id()))) {
      throw BusinessException.scopeDenied();
    }
  }

  /** 未获范围者不能通过ID试探范围外节点。 */
  private Department target(User actor, OrganizationFacts facts, Long id) {
    Long own = actor.department().id();
    if (!OrganizationPolicy.scope(facts, own).departmentIds().contains(id)) {
      if (OrganizationPolicy.global(facts, own)) {
        throw new BusinessException(404, "RESOURCE_NOT_FOUND", "部门不存在");
      }
      throw BusinessException.scopeDenied();
    }
    return facts.departments().stream()
        .filter(node -> node.id().equals(id))
        .findFirst()
        .orElseThrow(BusinessException::scopeDenied);
  }

  /** 同一上级仅允许一个同名有效部门。 */
  private void requireUnique(OrganizationFacts facts, Long parent, String name, Long existing) {
    if (facts.departments().stream()
        .anyMatch(
            node ->
                Objects.equals(parent, node.parentId())
                    && !node.id().equals(existing)
                    && node.name().equals(name.trim()))) {
      throw new BusinessException(409, "DEPARTMENT_NAME_CONFLICT", "同一上级下部门名称不能重复");
    }
  }

  /** 保存后用同一事务最新状态装配写响应。 */
  private DepartmentResult savedView(User actor, Long id) {
    return view.build(actor, scope.facts()).tree().stream()
        .filter(row -> id.equals(row.getId()))
        .findFirst()
        .orElseThrow(BusinessException::scopeDenied);
  }
}
