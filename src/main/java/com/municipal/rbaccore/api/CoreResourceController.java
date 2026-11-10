package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.*;
import com.municipal.rbaccore.api.vo.ResourceViews;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.CoreResourceService;
import com.municipal.rbaccore.service.ResourceModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** T06已评审资源接口的HTTP适配，不增加系统CRUD或API注册能力。 */
@RestController
@RequestMapping("/api")
@CoreManaged
@RequiredArgsConstructor
public class CoreResourceController {
  private final CoreResourceService service;

  /** 必要系统目录只返回RBAC，权限与共同根由统一服务检查。 */
  @GetMapping("/systems")
  public List<ResourceViews.SystemVO> systems(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.systems(header).stream()
        .map(result -> CloneFactory.copy(new ResourceViews.SystemVO(), result))
        .toList();
  }

  /** 树节点按契约递归映射为VO，保留停用节点与只读系统归属。 */
  @GetMapping("/permissions/tree")
  public List<ResourceViews.PermissionTreeVO> tree(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute ResourceSystemQueryDTO dto) {
    return service.tree(header, dto.getSystemId()).stream().map(this::treeView).toList();
  }

  /** 搜索与分页参数完成校验后由课程映射器转为服务命令。 */
  @GetMapping("/api-resources")
  public ResourceViews.ApiPageVO apis(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute ResourceApiQueryDTO dto) {
    ResourceModels.ApiPageResult result =
        service.apis(header, CloneFactory.copy(new ResourceModels.ApiSearch(), dto));
    ResourceViews.ApiPageVO view = CloneFactory.copy(new ResourceViews.ApiPageVO(), result);
    view.setItems(result.getItems().stream().map(this::apiView).toList());
    return view;
  }

  /** 已保存映射是独立完整集合，不从API候选分页拼接。 */
  @GetMapping("/permissions/{id}/apis")
  public ResourceViews.MappingVO mapping(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id) {
    return mappingView(service.mapping(header, id));
  }

  /** PUT只承载整体替换请求，未知关联或树/保护冲突由服务事务原子拒绝。 */
  @PutMapping("/permissions/{id}/apis")
  public ResourceViews.MappingVO replaceMapping(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody PermissionApisDTO dto) {
    return mappingView(
        service.replaceMapping(
            header, id, CloneFactory.copy(new ResourceModels.MappingCommand(), dto)));
  }

  /** PATCH区分省略与显式null，不开放未经评审的字段。 */
  @PatchMapping("/permissions/{id}")
  public ResourceViews.PermissionVO patchPermission(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody ResourcePermissionPatchDTO dto) {
    return CloneFactory.copy(
        new ResourceViews.PermissionVO(),
        service.patchPermission(
            header, id, CloneFactory.copy(new ResourceModels.PermissionPatch(), dto)));
  }

  /** API只启停，method/path/systemId始终只读。 */
  @PatchMapping("/api-resources/{id}")
  public ResourceViews.ApiVO patchApi(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(CoreApiLimits.MAX_ID) Long id,
      @Valid @RequestBody ResourceApiPatchDTO dto) {
    return apiView(
        service.patchApi(header, id, CloneFactory.copy(new ResourceModels.ApiPatch(), dto)));
  }

  /** 共同字段通过课程映射器转换，子元素类型变化显式递归。 */
  private ResourceViews.PermissionTreeVO treeView(ResourceModels.PermissionResult result) {
    ResourceViews.PermissionTreeVO view =
        CloneFactory.copy(new ResourceViews.PermissionTreeVO(), result);
    view.setChildren(result.getChildren().stream().map(this::treeView).toList());
    return view;
  }

  /** Result到VO的相同字段统一使用CloneFactory。 */
  private ResourceViews.ApiVO apiView(ResourceModels.ApiResult result) {
    return CloneFactory.copy(new ResourceViews.ApiVO(), result);
  }

  /** ids同类型直接复制，items元素类型转换独立处理。 */
  private ResourceViews.MappingVO mappingView(ResourceModels.MappingResult result) {
    ResourceViews.MappingVO view = CloneFactory.copy(new ResourceViews.MappingVO(), result);
    view.setItems(result.getItems().stream().map(this::apiView).toList());
    return view;
  }
}
