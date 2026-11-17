package com.municipal.rbaccore.api.role;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.role.dto.RoleCreateDTO;
import com.municipal.rbaccore.api.role.dto.RolePatchDTO;
import com.municipal.rbaccore.api.role.dto.RolePermissionsDTO;
import com.municipal.rbaccore.api.role.dto.RoleQueryDTO;
import com.municipal.rbaccore.api.role.vo.RolePageVO;
import com.municipal.rbaccore.api.role.vo.RolePermissionsVO;
import com.municipal.rbaccore.api.role.vo.RoleVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.role.CreateRoleCommand;
import com.municipal.rbaccore.service.port.role.ReplaceRolePermissionsCommand;
import com.municipal.rbaccore.service.port.role.RolePageResult;
import com.municipal.rbaccore.service.port.role.RolePermissionsResult;
import com.municipal.rbaccore.service.port.role.RoleQuery;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.role.RoleUseCase;
import com.municipal.rbaccore.service.port.role.UpdateRoleCommand;
import com.municipal.rbaccore.service.port.security.Managed;
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
@Managed
@RequiredArgsConstructor
public class RoleController {
  private final RoleUseCase service;

  /** 分页搜索角色，状态格式通过校验后才转换为Boolean命令。 */
  @GetMapping
  public RolePageVO list(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute RoleQueryDTO dto) {
    RoleQuery query = CloneFactory.copy(new RoleQuery(), dto);
    query.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    RolePageResult result = service.list(header, query);
    RolePageVO view = CloneFactory.copy(new RolePageVO(), result);
    view.setItems(result.getItems().stream().map(this::roleView).toList());
    return view;
  }

  /** 直接权限集合独立读取，不依赖权限树候选或列表页大小。 */
  @GetMapping("/{id}/permissions")
  public RolePermissionsVO selected(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id) {
    return permissionsView(service.selected(header, id));
  }

  /** POST返回201及新定义，后续授权仍通过独立PUT入口完成。 */
  @PostMapping
  public ResponseEntity<RoleVO> create(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @RequestBody RoleCreateDTO dto) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(roleView(service.create(header, CloneFactory.copy(new CreateRoleCommand(), dto))));
  }

  /** PATCH只维护名称和状态，不接受编码、系统或直接权限字段。 */
  @PatchMapping("/{id}")
  public RoleVO patch(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody RolePatchDTO dto) {
    return roleView(service.patch(header, id, CloneFactory.copy(new UpdateRoleCommand(), dto)));
  }

  /** 显式空数组清空，重复、null或非法ID在进入服务前拒绝。 */
  @PutMapping("/{id}/permissions")
  public RolePermissionsVO replacePermissions(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody RolePermissionsDTO dto) {
    return permissionsView(
        service.replacePermissions(
            header, id, CloneFactory.copy(new ReplaceRolePermissionsCommand(), dto)));
  }

  /** 相同字段使用课程映射器，避免手工逐字段复制。 */
  private RoleVO roleView(RoleResult result) {
    return CloneFactory.copy(new RoleVO(), result);
  }

  /** 结果对象与对外VO分离，直接权限ID保持既定顺序。 */
  private RolePermissionsVO permissionsView(RolePermissionsResult result) {
    return CloneFactory.copy(new RolePermissionsVO(), result);
  }
}
