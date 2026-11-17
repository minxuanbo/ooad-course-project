package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.user.UserResult;
import com.municipal.rbaccore.service.port.user.UserWithRolesResult;
import java.util.Comparator;
import java.util.List;

/** 将不可变领域聚合显式映射为公开契约结果，不暴露口令或手机号。 */
public final class DddResults {
  private DddResults() {}

  /** 角色状态和固定级别完整返回。 */
  public static RoleResult role(Role value) {
    RoleResult result = new RoleResult();
    result.setId(value.id());
    result.setCode(value.code());
    result.setName(value.name());
    result.setManagementLevel(value.managementLevel());
    result.setEnabled(value.enabled());
    return result;
  }

  /** 按角色ID稳定输出已经分配的完整摘要。 */
  public static List<RoleResult> assigned(User user) {
    return user.roles().stream()
        .sorted(Comparator.comparing(Role::id))
        .map(DddResults::role)
        .toList();
  }

  /** 人员查询包含原职位和当前状态。 */
  public static UserResult person(User user, boolean writable) {
    UserResult result = new UserResult();
    result.setId(user.id());
    result.setUsername(user.username());
    result.setName(user.name());
    result.setPosition(user.position());
    result.setDepartmentId(user.department().id());
    result.setDepartmentName(user.department().name());
    result.setEnabled(user.enabled());
    result.setWritable(writable);
    result.setRoles(assigned(user));
    return result;
  }

  /** 管理动作只返回契约明确的人员字段及已保存角色编号。 */
  public static UserWithRolesResult updated(User user) {
    UserWithRolesResult result = new UserWithRolesResult();
    result.setId(user.id());
    result.setUsername(user.username());
    result.setName(user.name());
    result.setDepartmentId(user.department().id());
    result.setEnabled(user.enabled());
    result.setRoleIds(user.roles().stream().map(Role::id).sorted().toList());
    return result;
  }
}
