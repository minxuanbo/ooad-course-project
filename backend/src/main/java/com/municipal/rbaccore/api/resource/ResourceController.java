package com.municipal.rbaccore.api.resource;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.resource.dto.PermissionApisDTO;
import com.municipal.rbaccore.api.resource.dto.ResourceApiPatchDTO;
import com.municipal.rbaccore.api.resource.dto.ResourceApiQueryDTO;
import com.municipal.rbaccore.api.resource.dto.ResourcePermissionPatchDTO;
import com.municipal.rbaccore.api.resource.dto.ResourceSystemQueryDTO;
import com.municipal.rbaccore.api.resource.vo.ApiPageVO;
import com.municipal.rbaccore.api.resource.vo.ApiVO;
import com.municipal.rbaccore.api.resource.vo.PermissionApisVO;
import com.municipal.rbaccore.api.resource.vo.PermissionTreeVO;
import com.municipal.rbaccore.api.resource.vo.ResourcePermissionVO;
import com.municipal.rbaccore.api.resource.vo.SystemVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.resource.ApiPageResult;
import com.municipal.rbaccore.service.port.resource.ApiQuery;
import com.municipal.rbaccore.service.port.resource.ApiResult;
import com.municipal.rbaccore.service.port.resource.PermissionApisResult;
import com.municipal.rbaccore.service.port.resource.ReplacePermissionApisCommand;
import com.municipal.rbaccore.service.port.resource.ResourcePermissionResult;
import com.municipal.rbaccore.service.port.resource.ResourceUseCase;
import com.municipal.rbaccore.service.port.resource.UpdateApiCommand;
import com.municipal.rbaccore.service.port.resource.UpdatePermissionCommand;
import com.municipal.rbaccore.service.port.security.Managed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** T06已评审资源接口的HTTP适配，不增加系统CRUD或API注册能力。 */
@RestController
@RequestMapping("/api")
@Managed
@RequiredArgsConstructor
public class ResourceController {
  private final ResourceUseCase service;

  /** 必要系统目录只返回RBAC，权限与共同根由统一服务检查。 */
  @GetMapping("/systems")
  public List<SystemVO> systems(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.systems(header).stream()
        .map(result -> CloneFactory.copy(new SystemVO(), result))
        .toList();
  }

  /** 树节点按契约递归映射为VO，保留停用节点与只读系统归属。 */
  @GetMapping("/permissions/tree")
  public List<PermissionTreeVO> tree(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute ResourceSystemQueryDTO dto) {
    return service.tree(header, dto.getSystemId()).stream().map(this::treeView).toList();
  }

  /** 搜索与分页参数完成校验后由课程映射器转为服务命令。 */
  @GetMapping("/api-resources")
  public ApiPageVO apis(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute ResourceApiQueryDTO dto) {
    ApiPageResult result = service.apis(header, CloneFactory.copy(new ApiQuery(), dto));
    ApiPageVO view = CloneFactory.copy(new ApiPageVO(), result);
    view.setItems(result.getItems().stream().map(this::apiView).toList());
    return view;
  }

  /** 已保存映射是独立完整集合，不从API候选分页拼接。 */
  @GetMapping("/permissions/{id}/apis")
  public PermissionApisVO mapping(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id) {
    return mappingView(service.mapping(header, id));
  }

  /** PUT只承载整体替换请求，未知关联或树/保护冲突由服务事务原子拒绝。 */
  @PutMapping("/permissions/{id}/apis")
  public PermissionApisVO replaceMapping(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody PermissionApisDTO dto) {
    return mappingView(
        service.replaceMapping(
            header, id, CloneFactory.copy(new ReplacePermissionApisCommand(), dto)));
  }

  /** PATCH区分省略与显式null，不开放未经评审的字段。 */
  @PatchMapping("/permissions/{id}")
  public ResourcePermissionVO patchPermission(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody ResourcePermissionPatchDTO dto) {
    return CloneFactory.copy(
        new ResourcePermissionVO(),
        service.patchPermission(header, id, CloneFactory.copy(new UpdatePermissionCommand(), dto)));
  }

  /** API只启停，method/path/systemId始终只读。 */
  @PatchMapping("/api-resources/{id}")
  public ApiVO patchApi(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody ResourceApiPatchDTO dto) {
    return apiView(service.patchApi(header, id, CloneFactory.copy(new UpdateApiCommand(), dto)));
  }

  /** 共同字段通过课程映射器转换，子元素类型变化显式递归。 */
  private PermissionTreeVO treeView(ResourcePermissionResult result) {
    PermissionTreeVO view = CloneFactory.copy(new PermissionTreeVO(), result);
    view.setChildren(result.getChildren().stream().map(this::treeView).toList());
    return view;
  }

  /** Result到VO的相同字段统一使用CloneFactory。 */
  private ApiVO apiView(ApiResult result) {
    return CloneFactory.copy(new ApiVO(), result);
  }

  /** ids同类型直接复制，items元素类型转换独立处理。 */
  private PermissionApisVO mappingView(PermissionApisResult result) {
    PermissionApisVO view = CloneFactory.copy(new PermissionApisVO(), result);
    view.setItems(result.getItems().stream().map(this::apiView).toList());
    return view;
  }
}
