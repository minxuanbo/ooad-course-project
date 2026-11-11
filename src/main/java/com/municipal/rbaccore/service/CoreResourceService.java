package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import com.municipal.rbaccore.security.RbacCatalog;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

/** 当前RBAC资源的必要查询与维护，范围、树规则和完整K在服务事务内完成。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreResourceService {
  private final ApplicationSystemRepository systems;
  private final PermissionRepository permissions;
  private final ApiResourceRepository apis;
  private final GlobalManagementAccess access;

  /** 仅选择本权限系统；目录查询本身也必须满足实际API和共同根。 */
  public List<ResourceModels.SystemResult> systems(String header) {
    access.require(header);
    ApplicationSystem system = currentSystem();
    ResourceModels.SystemResult result =
        CloneFactory.copy(new ResourceModels.SystemResult(), system);
    result.setEnabled(system.isEnabled());
    return List.of(result);
  }

  /** 同系统权限一次读取，按ID组合树，保留停用项且不补角色授权。 */
  public List<ResourceModels.PermissionResult> tree(String header, Long systemId) {
    access.require(header);
    ApplicationSystem system = selectSystem(systemId);
    Map<Long, ResourceModels.PermissionResult> indexed = new LinkedHashMap<>();
    for (Permission permission : permissions.findBySystemIdOrderByIdAsc(system.getId())) {
      ResourceModels.PermissionResult result = permissionResult(permission);
      result.setChildren(new ArrayList<>());
      indexed.put(permission.getId(), result);
    }
    List<ResourceModels.PermissionResult> roots = new ArrayList<>();
    for (ResourceModels.PermissionResult node : indexed.values()) {
      if (node.getParentId() == null) roots.add(node);
      else {
        ResourceModels.PermissionResult parent = indexed.get(node.getParentId());
        if (parent == null)
          throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "权限树父级不在当前系统");
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  /** 同一个系统及搜索谓词负责范围、分页和总数，极大越末页不进入JPA偏移转换。 */
  public ResourceModels.ApiPageResult apis(String header, ResourceModels.ApiSearch query) {
    access.require(header);
    ApplicationSystem system = selectSystem(query.getSystemId());
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    Specification<ApiResource> filter =
        (root, criteria, builder) ->
            builder.and(
                builder.equal(root.get("system").get("id"), system.getId()),
                builder.or(
                    builder.like(builder.lower(root.get("httpMethod")), pattern, '\\'),
                    builder.like(builder.lower(root.get("pathTemplate")), pattern, '\\')));
    long total = apis.count(filter);
    if ((long) query.getPage() * query.getSize() >= total) {
      return new ResourceModels.ApiPageResult(List.of(), total, query.getPage(), query.getSize());
    }
    Page<ApiResource> page =
        apis.findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")));
    return new ResourceModels.ApiPageResult(
        page.getContent().stream().map(this::apiResult).toList(),
        total,
        query.getPage(),
        query.getSize());
  }

  /** 已保存映射独立于候选列表，完整返回停用API和稳定顺序。 */
  public ResourceModels.MappingResult mapping(String header, Long permissionId) {
    access.require(header);
    return mappingResult(permission(permissionId));
  }

  /** MVC解析输入前先检查合法目标的系统范围；实际写事务拿锁后仍完整复核。 */
  public void checkPermissionTarget(String header, Long id) {
    access.require(header);
    permission(id);
  }

  /** 直接API目标的全局及系统范围先于请求体校验，不接受前端授权结论。 */
  public void checkApiTarget(String header, Long id) {
    access.require(header);
    api(id);
  }

  /** 系统目录范围检查不泄漏外系统存在性，后续参数校验仍留在API层。 */
  public void checkSystemTarget(String header, Long id) {
    access.require(header);
    selectSystem(id);
  }

  /** 先校验整组关联，再整体替换；无变化不重复插入，停用API允许保存但不贡献授权。 */
  @Transactional
  public ResourceModels.MappingResult replaceMapping(
      String header, Long permissionId, ResourceModels.MappingCommand command) {
    return write(
        header,
        () -> {
          Permission permission = permission(permissionId);
          List<ApiResource> selected = apis.findAllById(command.getIds());
          if (selected.size() != command.getIds().size()
              || selected.stream()
                  .anyMatch(
                      api -> !api.getSystem().getId().equals(permission.getSystem().getId()))) {
            throw ApiException.bad("INVALID_ARGUMENT", "API不存在或不属于当前系统");
          }
          Set<Long> wanted = new HashSet<>(command.getIds());
          for (ApiResource current : List.copyOf(permission.getApis())) {
            if (!wanted.contains(current.getId())) permission.unmapApi(current);
          }
          for (ApiResource api : selected) permission.mapApi(api);
          permissions.saveAndFlush(permission);
          return mappingResult(permission);
        });
  }

  /** 不支持系统/编码/名称编辑，显式父级null与缺省保持不同含义。 */
  @Transactional
  public ResourceModels.PermissionResult patchPermission(
      String header, Long id, ResourceModels.PermissionPatch command) {
    return write(
        header,
        () -> {
          Permission permission = permission(id);
          Permission parent = null;
          if (Boolean.TRUE.equals(command.getParentIdPresent()) && command.getParentId() != null) {
            parent =
                permissions
                    .findById(command.getParentId())
                    .orElseThrow(() -> ApiException.bad("INVALID_ARGUMENT", "父权限不存在"));
            if (!parent.getSystem().getId().equals(permission.getSystem().getId())) {
              throw ApiException.bad("INVALID_ARGUMENT", "父子权限必须属于同一系统");
            }
          }
          try {
            if (command.getType() != null) permission.changeType(command.getType());
            if (Boolean.TRUE.equals(command.getParentIdPresent())) permission.moveTo(parent);
          } catch (IllegalArgumentException exception) {
            throw ApiException.bad("INVALID_ARGUMENT", "权限父级或类型违反树约束");
          }
          if (command.getEnabled() != null) permission.setEnabled(command.getEnabled());
          permissions.saveAndFlush(permission);
          return permissionResult(permission);
        });
  }

  /** API启停保留所有引用，修改后按同用户完整K验证恢复维护能力。 */
  @Transactional
  public ResourceModels.ApiResult patchApi(
      String header, Long id, ResourceModels.ApiPatch command) {
    return write(
        header,
        () -> {
          ApiResource api = api(id);
          api.setEnabled(command.getEnabled());
          apis.saveAndFlush(api);
          return apiResult(api);
        });
  }

  /** 所有资源写入共用根锁；拿锁后再读身份/授权，数据库错误在实际保存边界翻译。 */
  private <T> T write(String header, Supplier<T> change) {
    try {
      return access.write(header, change);
    } catch (DataAccessException | TransactionException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "资源保存冲突，请刷新后重试");
    }
  }

  /** 外系统及未知系统查询统一按范围拒绝，不泄露外系统存在性。 */
  private ApplicationSystem selectSystem(Long requested) {
    ApplicationSystem system = currentSystem();
    if (requested != null && !requested.equals(system.getId())) throw ApiException.scopeDenied();
    return system;
  }

  /** 稳定code仅定位系统，不能用固定主键或角色名称替代授权。 */
  private ApplicationSystem currentSystem() {
    return systems.findByCode(RbacCatalog.SYSTEM_CODE).orElseThrow(CoreResourceService::notFound);
  }

  /** 直接目标的系统边界先于关联输入校验；未知范围内目标404。 */
  private Permission permission(Long id) {
    Permission permission = permissions.findById(id).orElseThrow(CoreResourceService::notFound);
    if (!permission.getSystem().getId().equals(currentSystem().getId()))
      throw ApiException.scopeDenied();
    return permission;
  }

  /** API直接目标与权限目标共用同一系统范围语义。 */
  private ApiResource api(Long id) {
    ApiResource api = apis.findById(id).orElseThrow(CoreResourceService::notFound);
    if (!api.getSystem().getId().equals(currentSystem().getId())) throw ApiException.scopeDenied();
    return api;
  }

  /** 同名字段由课程映射器复制，关系及primitive布尔值显式转换。 */
  private ResourceModels.PermissionResult permissionResult(Permission permission) {
    ResourceModels.PermissionResult result =
        CloneFactory.copy(new ResourceModels.PermissionResult(), permission);
    result.setSystemId(permission.getSystem().getId());
    result.setParentId(permission.getParent() == null ? null : permission.getParent().getId());
    result.setEnabled(permission.isEnabled());
    return result;
  }

  /** 实体字段命名与已评审HTTP契约不同，方法/模板明确转换。 */
  private ResourceModels.ApiResult apiResult(ApiResource api) {
    ResourceModels.ApiResult result = CloneFactory.copy(new ResourceModels.ApiResult(), api);
    result.setSystemId(api.getSystem().getId());
    result.setMethod(api.getHttpMethod());
    result.setPath(api.getPathTemplate());
    result.setEnabled(api.isEnabled());
    return result;
  }

  /** 映射按API主键排序，ids与items始终表达同一个完整集合。 */
  private ResourceModels.MappingResult mappingResult(Permission permission) {
    List<ApiResource> selected =
        permission.getApis().stream().sorted(Comparator.comparing(ApiResource::getId)).toList();
    return new ResourceModels.MappingResult(
        selected.stream().map(ApiResource::getId).toList(),
        selected.stream().map(this::apiResult).toList());
  }

  /** 未知目标不泄漏表名或内部异常。 */
  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在");
  }
}
