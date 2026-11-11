package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.*;
import com.municipal.rbaccore.api.vo.RoleViews;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.CoreRoleService;
import com.municipal.rbaccore.service.RoleModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** T08已评审的五个角色接口，HTTP校验和DTO/VO映射留在API层。 */
@RestController
@RequestMapping("/api/roles")
@CoreManaged
@RequiredArgsConstructor
public class CoreRoleController {
  private final CoreRoleService service;

  /** 分页搜索角色，状态格式通过校验后才转换为Boolean命令。 */
  @GetMapping
  public RoleViews.PageVO list(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute RoleQueryDTO dto) {
    RoleModels.Search query = CloneFactory.copy(new RoleModels.Search(), dto);
    query.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    RoleModels.PageResult result = service.list(header, query);
    RoleViews.PageVO view = CloneFactory.copy(new RoleViews.PageVO(), result);
    view.setItems(result.getItems().stream().map(this::roleView).toList());
    return view;
  }

  /** 直接权限集合独立读取，不依赖权限树候选或列表页大小。 */
  @GetMapping("/{id}/permissions")
  public RoleViews.PermissionsVO selected(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id) {
    return permissionsView(service.selected(header, id));
  }

  /** POST返回201及新定义，后续授权仍通过独立PUT入口完成。 */
  @PostMapping
  public ResponseEntity<RoleViews.RoleVO> create(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @RequestBody RoleCreateDTO dto) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            roleView(
                service.create(header, CloneFactory.copy(new RoleModels.CreateCommand(), dto))));
  }

  /** PATCH只维护名称和状态，不接受编码、系统或直接权限字段。 */
  @PatchMapping("/{id}")
  public RoleViews.RoleVO patch(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody RolePatchDTO dto) {
    return roleView(
        service.patch(header, id, CloneFactory.copy(new RoleModels.PatchCommand(), dto)));
  }

  /** 显式空数组清空，重复、null或非法ID在进入服务前拒绝。 */
  @PutMapping("/{id}/permissions")
  public RoleViews.PermissionsVO replacePermissions(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody RolePermissionsDTO dto) {
    return permissionsView(
        service.replacePermissions(
            header, id, CloneFactory.copy(new RoleModels.PermissionsCommand(), dto)));
  }

  /** 相同字段使用课程映射器，避免手工逐字段复制。 */
  private RoleViews.RoleVO roleView(RoleModels.RoleResult result) {
    return CloneFactory.copy(new RoleViews.RoleVO(), result);
  }

  /** 结果对象与对外VO分离，直接权限ID保持既定顺序。 */
  private RoleViews.PermissionsVO permissionsView(RoleModels.PermissionsResult result) {
    return CloneFactory.copy(new RoleViews.PermissionsVO(), result);
  }
}
