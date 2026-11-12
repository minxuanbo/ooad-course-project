package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.UserAssignableQueryDTO;
import com.municipal.rbaccore.api.dto.UserDepartmentDTO;
import com.municipal.rbaccore.api.dto.UserQueryDTO;
import com.municipal.rbaccore.api.dto.UserRolesDTO;
import com.municipal.rbaccore.api.dto.UserStatusDTO;
import com.municipal.rbaccore.api.vo.CoreViews;
import com.municipal.rbaccore.api.vo.RoleViews;
import com.municipal.rbaccore.api.vo.UserAdminViews;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.CoreResults;
import com.municipal.rbaccore.service.CoreUserAdminService;
import com.municipal.rbaccore.service.CoreUserService;
import com.municipal.rbaccore.service.RoleModels;
import com.municipal.rbaccore.service.UserAdminModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本轮唯一人员列表、部门导航及T10人员角色/停用/调动入口。 */
@RestController
@RequestMapping("/api")
@CoreManaged
@RequiredArgsConstructor
public class CoreUserController {
  private final CoreUserService service;
  private final CoreUserAdminService admin;

  /** 先在API边界校验参数，再映射成独立查询命令。 */
  @GetMapping("/users")
  public CoreViews.UserPageVO users(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute UserQueryDTO dto) {
    CoreResults.UserSearch command = CloneFactory.copy(new CoreResults.UserSearch(), dto);
    command.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    CoreResults.UserPageResult result = service.list(header, command);
    CoreViews.UserPageVO view = CloneFactory.copy(new CoreViews.UserPageVO(), result);
    view.setItems(
        result.getItems().stream()
            .map(item -> CloneFactory.copy(new CoreViews.UserVO(), item))
            .toList());
    return view;
  }

  /** 返回可见范围的扁平树，客户端不得自行裁剪全部组织。 */
  @GetMapping("/departments/tree")
  public List<CoreViews.DepartmentVO> tree(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.tree(header).stream()
        .map(item -> CloneFactory.copy(new CoreViews.DepartmentVO(), item))
        .toList();
  }

  /** 当前角色读取包含停用及现已不可分配项，供整体替换前清理。 */
  @GetMapping("/users/{id}/roles")
  public UserAdminViews.RolesViewVO roles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id) {
    UserAdminModels.RolesViewResult result = admin.roles(header, id);
    UserAdminViews.RolesViewVO view = CloneFactory.copy(new UserAdminViews.RolesViewVO(), result);
    view.setUser(userView(result.getUser()));
    view.setRoles(result.getRoles().stream().map(this::roleView).toList());
    return view;
  }

  /** 候选角色由服务端按目标范围及本人最新有效权限计算，前端不提交候选。 */
  @GetMapping("/users/{id}/assignable-roles")
  public UserAdminViews.AssignablePageVO assignableRoles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @ModelAttribute UserAssignableQueryDTO dto) {
    UserAdminModels.AssignablePageResult result =
        admin.assignableRoles(
            header, id, CloneFactory.copy(new UserAdminModels.AssignableSearch(), dto));
    UserAdminViews.AssignablePageVO view =
        CloneFactory.copy(new UserAdminViews.AssignablePageVO(), result);
    view.setItems(result.getItems().stream().map(this::roleView).toList());
    return view;
  }

  /** 显式空数组清空全部关联，整组校验与发布在同一写事务内完成。 */
  @PutMapping("/users/{id}/roles")
  public UserAdminViews.UserWithRolesVO replaceRoles(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserRolesDTO dto) {
    return userView(
        admin.replaceRoles(
            header, id, CloneFactory.copy(new UserAdminModels.ReplaceRolesCommand(), dto)));
  }

  /** 只处理停用，不提供人员恢复或删除入口。 */
  @PatchMapping("/users/{id}/status")
  public UserAdminViews.UserWithRolesVO patchStatus(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserStatusDTO dto) {
    return userView(admin.disable(header, id));
  }

  /** 单一部门整体替换，原/目标范围与保护条件在锁内重新校验。 */
  @PutMapping("/users/{id}/department")
  public UserAdminViews.UserWithRolesVO moveDepartment(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody UserDepartmentDTO dto) {
    return userView(
        admin.move(header, id, CloneFactory.copy(new UserAdminModels.MoveCommand(), dto)));
  }

  /** 相同字段使用课程映射器，避免手工逐字段复制。 */
  private UserAdminViews.UserWithRolesVO userView(UserAdminModels.UserWithRolesResult result) {
    return CloneFactory.copy(new UserAdminViews.UserWithRolesVO(), result);
  }

  /** 结果对象与对外VO分离，角色状态由服务结果显式提供。 */
  private RoleViews.RoleVO roleView(RoleModels.RoleResult result) {
    return CloneFactory.copy(new RoleViews.RoleVO(), result);
  }
}
