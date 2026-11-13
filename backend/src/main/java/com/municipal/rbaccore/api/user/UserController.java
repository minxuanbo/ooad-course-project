package com.municipal.rbaccore.api.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.role.vo.RoleVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.api.user.dto.UserAssignableQueryDTO;
import com.municipal.rbaccore.api.user.dto.UserDepartmentDTO;
import com.municipal.rbaccore.api.user.dto.UserQueryDTO;
import com.municipal.rbaccore.api.user.dto.UserRolesDTO;
import com.municipal.rbaccore.api.user.dto.UserStatusDTO;
import com.municipal.rbaccore.api.user.vo.AssignableRolePageVO;
import com.municipal.rbaccore.api.user.vo.DepartmentVO;
import com.municipal.rbaccore.api.user.vo.UserPageVO;
import com.municipal.rbaccore.api.user.vo.UserRolesVO;
import com.municipal.rbaccore.api.user.vo.UserVO;
import com.municipal.rbaccore.api.user.vo.UserWithRolesVO;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.security.Managed;
import com.municipal.rbaccore.service.port.user.AssignableRolePageResult;
import com.municipal.rbaccore.service.port.user.AssignableRoleQuery;
import com.municipal.rbaccore.service.port.user.ReplaceUserRolesCommand;
import com.municipal.rbaccore.service.port.user.TransferUserCommand;
import com.municipal.rbaccore.service.port.user.UserManagementUseCase;
import com.municipal.rbaccore.service.port.user.UserPageResult;
import com.municipal.rbaccore.service.port.user.UserQuery;
import com.municipal.rbaccore.service.port.user.UserQueryUseCase;
import com.municipal.rbaccore.service.port.user.UserRolesResult;
import com.municipal.rbaccore.service.port.user.UserWithRolesResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本轮唯一人员列表、部门导航及T10人员角色/停用/调动入口。 */
@RestController
@RequestMapping("/api")
@Managed
@RequiredArgsConstructor
public class UserController {
  private final UserQueryUseCase service;
  private final UserManagementUseCase admin;

  /** 先在API边界校验参数，再映射成独立查询命令。 */
  @GetMapping("/users")
  public UserPageVO users(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute UserQueryDTO dto) {
    UserQuery command = CloneFactory.copy(new UserQuery(), dto);
    command.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    UserPageResult result = service.list(header, command);
    UserPageVO view = CloneFactory.copy(new UserPageVO(), result);
    view.setItems(
        result.getItems().stream()
            .map(
                item -> {
                  UserVO row = CloneFactory.copy(new UserVO(), item);
                  row.setRoles(item.getRoles().stream().map(this::roleView).toList());
                  return row;
                })
            .toList());
    return view;
  }

  /** 返回可见范围的扁平树，客户端不得自行裁剪全部组织。 */
  @GetMapping("/departments/tree")
  public List<DepartmentVO> tree(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.tree(header).stream()
        .map(item -> CloneFactory.copy(new DepartmentVO(), item))
        .toList();
  }

  /** 当前角色读取包含停用及现已不可分配项，供整体替换前清理。 */
  @GetMapping("/users/{id}/roles")
  public UserRolesVO roles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id) {
    UserRolesResult result = admin.roles(header, id);
    UserRolesVO view = CloneFactory.copy(new UserRolesVO(), result);
    view.setUser(userView(result.getUser()));
    view.setRoles(result.getRoles().stream().map(this::roleView).toList());
    return view;
  }

  /** 候选角色由服务端按目标范围及本人最新有效权限计算，前端不提交候选。 */
  @GetMapping("/users/{id}/assignable-roles")
  public AssignableRolePageVO assignableRoles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @ModelAttribute UserAssignableQueryDTO dto) {
    AssignableRolePageResult result =
        admin.assignableRoles(header, id, CloneFactory.copy(new AssignableRoleQuery(), dto));
    AssignableRolePageVO view = CloneFactory.copy(new AssignableRolePageVO(), result);
    view.setItems(result.getItems().stream().map(this::roleView).toList());
    return view;
  }

  /** 显式空数组清空全部关联，整组校验与发布在同一写事务内完成。 */
  @PutMapping("/users/{id}/roles")
  public UserWithRolesVO replaceRoles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserRolesDTO dto) {
    return userView(
        admin.replaceRoles(header, id, CloneFactory.copy(new ReplaceUserRolesCommand(), dto)));
  }

  /** 只处理停用，不提供人员恢复或删除入口。 */
  @PatchMapping("/users/{id}/status")
  public UserWithRolesVO patchStatus(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserStatusDTO dto) {
    return userView(admin.disable(header, id));
  }

  /** 单一部门整体替换，原/目标范围与保护条件在锁内重新校验。 */
  @PutMapping("/users/{id}/department")
  public UserWithRolesVO moveDepartment(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserDepartmentDTO dto) {
    return userView(admin.move(header, id, CloneFactory.copy(new TransferUserCommand(), dto)));
  }

  /** 相同字段使用课程映射器，避免手工逐字段复制。 */
  private UserWithRolesVO userView(UserWithRolesResult result) {
    return CloneFactory.copy(new UserWithRolesVO(), result);
  }

  /** 结果对象与对外VO分离，角色状态由服务结果显式提供。 */
  private RoleVO roleView(RoleResult result) {
    return CloneFactory.copy(new RoleVO(), result);
  }
}
