package com.municipal.rbaccore.service.structured.resource;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApiResourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.ApiPageResult;
import com.municipal.rbaccore.service.port.resource.ApiQuery;
import com.municipal.rbaccore.service.port.resource.ApiResult;
import com.municipal.rbaccore.service.port.resource.PermissionApisResult;
import com.municipal.rbaccore.service.port.resource.ReplacePermissionApisCommand;
import com.municipal.rbaccore.service.port.resource.ResourcePermissionResult;
import com.municipal.rbaccore.service.port.resource.ResourceUseCase;
import com.municipal.rbaccore.service.port.resource.SystemResult;
import com.municipal.rbaccore.service.port.resource.UpdateApiCommand;
import com.municipal.rbaccore.service.port.resource.UpdatePermissionCommand;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import com.municipal.rbaccore.service.structured.security.StructuredGlobalManagementAccess;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

/** 当前RBAC资源的必要查询与维护，范围、树规则和完整K在服务事务内完成。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredResourceService implements ResourceUseCase {
  private final ApplicationSystemJpaRepository systems;
  private final PermissionJpaRepository permissions;
  private final ApiResourceJpaRepository apis;
  private final StructuredGlobalManagementAccess access;

  /** 仅选择本权限系统；目录查询本身也必须满足实际API和共同根。 */
  public List<SystemResult> systems(String header) {
    access.require(header);
    ApplicationSystemEntity system = currentSystem();
    SystemResult result = CloneFactory.copy(new SystemResult(), system);
    result.setEnabled(system.isEnabled());
    return List.of(result);
  }

  /** 同系统权限一次读取，按ID组合树，保留停用项且不补角色授权。 */
  public List<ResourcePermissionResult> tree(String header, Long systemId) {
    access.require(header);
    ApplicationSystemEntity system = selectSystem(systemId);
    Map<Long, ResourcePermissionResult> indexed = new LinkedHashMap<>();
    for (PermissionEntity permission : permissions.findBySystemIdOrderByIdAsc(system.getId())) {
      ResourcePermissionResult result = permissionResult(permission);
      result.setChildren(new ArrayList<>());
      indexed.put(permission.getId(), result);
    }
    List<ResourcePermissionResult> roots = new ArrayList<>();
    for (ResourcePermissionResult node : indexed.values()) {
      if (node.getParentId() == null) roots.add(node);
      else {
        ResourcePermissionResult parent = indexed.get(node.getParentId());
        if (parent == null) throw new BusinessException(409, "DATA_CONFLICT", "权限树父级不在当前系统");
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  /** 同一个系统及搜索谓词负责范围、分页和总数，极大越末页不进入JPA偏移转换。 */
  public ApiPageResult apis(String header, ApiQuery query) {
    access.require(header);
    ApplicationSystemEntity system = selectSystem(query.getSystemId());
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    Specification<ApiResourceEntity> filter =
        (root, criteria, builder) ->
            builder.and(
                builder.equal(root.get("system").get("id"), system.getId()),
                builder.or(
                    builder.like(builder.lower(root.get("httpMethod")), pattern, '\\'),
                    builder.like(builder.lower(root.get("pathTemplate")), pattern, '\\')));
    long total = apis.count(filter);
    if ((long) query.getPage() * query.getSize() >= total) {
      return new ApiPageResult(List.of(), total, query.getPage(), query.getSize());
    }
    Page<ApiResourceEntity> page =
        apis.findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")));
    return new ApiPageResult(
        page.getContent().stream().map(this::apiResult).toList(),
        total,
        query.getPage(),
        query.getSize());
  }

  /** 已保存映射独立于候选列表，完整返回停用API和稳定顺序。 */
  public PermissionApisResult mapping(String header, Long permissionId) {
    access.require(header);
    return mappingResult(permission(permissionId));
  }

  /** MVC解析输入前先检查合法目标的系统范围；实际写事务拿锁后仍完整复核。 */
  public void checkPermissionTarget(String header, Long id) {
    access.require(header);
    /** 构造本测试系统中的权限夹具，不生成额外授权。 */
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
  public PermissionApisResult replaceMapping(
      String header, Long permissionId, ReplacePermissionApisCommand command) {
    return write(
        header,
        () -> {
          PermissionEntity permission = permission(permissionId);
          List<ApiResourceEntity> selected = apis.findAllById(command.getIds());
          if (selected.size() != command.getIds().size()
              || selected.stream()
                  .anyMatch(
                      api -> !api.getSystem().getId().equals(permission.getSystem().getId()))) {
            throw BusinessException.bad("INVALID_ARGUMENT", "API不存在或不属于当前系统");
          }
          Set<Long> wanted = new HashSet<>(command.getIds());
          for (ApiResourceEntity current : List.copyOf(permission.getApis())) {
            if (!wanted.contains(current.getId())) permission.unmapApi(current);
          }
          for (ApiResourceEntity api : selected) permission.mapApi(api);
          permissions.saveAndFlush(permission);
          return mappingResult(permission);
        });
  }

  /** 不支持系统/编码/名称编辑，显式父级null与缺省保持不同含义。 */
  @Transactional
  public ResourcePermissionResult patchPermission(
      String header, Long id, UpdatePermissionCommand command) {
    return write(
        header,
        () -> {
          PermissionEntity permission = permission(id);
          PermissionEntity parent = null;
          if (Boolean.TRUE.equals(command.getParentIdPresent()) && command.getParentId() != null) {
            parent =
                permissions
                    .findById(command.getParentId())
                    .orElseThrow(() -> BusinessException.bad("INVALID_ARGUMENT", "父权限不存在"));
            if (!parent.getSystem().getId().equals(permission.getSystem().getId())) {
              throw BusinessException.bad("INVALID_ARGUMENT", "父子权限必须属于同一系统");
            }
          }
          try {
            if (command.getType() != null) permission.changeType(command.getType());
            if (Boolean.TRUE.equals(command.getParentIdPresent())) permission.moveTo(parent);
          } catch (IllegalArgumentException exception) {
            throw BusinessException.bad("INVALID_ARGUMENT", "权限父级或类型违反树约束");
          }
          if (command.getEnabled() != null) permission.setEnabled(command.getEnabled());
          permissions.saveAndFlush(permission);
          return permissionResult(permission);
        });
  }

  /** API启停保留所有引用，修改后按同用户完整K验证恢复维护能力。 */
  @Transactional
  public ApiResult patchApi(String header, Long id, UpdateApiCommand command) {
    return write(
        header,
        () -> {
          ApiResourceEntity api = api(id);
          CloneFactory.copyNotNull(api, command);
          apis.saveAndFlush(api);
          return apiResult(api);
        });
  }

  /** 所有资源写入共用根锁；拿锁后再读身份/授权，数据库错误在实际保存边界翻译。 */
  private <T> T write(String header, Supplier<T> change) {
    try {
      return access.write(header, change);
    } catch (DataAccessException | TransactionException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "资源保存冲突，请刷新后重试");
    }
  }

  /** 外系统及未知系统查询统一按范围拒绝，不泄露外系统存在性。 */
  private ApplicationSystemEntity selectSystem(Long requested) {
    ApplicationSystemEntity system = currentSystem();
    if (requested != null && !requested.equals(system.getId()))
      throw BusinessException.scopeDenied();
    return system;
  }

  /** 稳定code仅定位系统，不能用固定主键或角色名称替代授权。 */
  private ApplicationSystemEntity currentSystem() {
    return systems
        .findByCode(RbacCatalog.SYSTEM_CODE)
        .orElseThrow(StructuredResourceService::notFound);
  }

  /** 直接目标的系统边界先于关联输入校验；未知范围内目标404。 */
  private PermissionEntity permission(Long id) {
    PermissionEntity permission =
        permissions.findById(id).orElseThrow(StructuredResourceService::notFound);
    if (!permission.getSystem().getId().equals(currentSystem().getId()))
      throw BusinessException.scopeDenied();
    return permission;
  }

  /** API直接目标与权限目标共用同一系统范围语义。 */
  private ApiResourceEntity api(Long id) {
    ApiResourceEntity api = apis.findById(id).orElseThrow(StructuredResourceService::notFound);
    if (!api.getSystem().getId().equals(currentSystem().getId()))
      throw BusinessException.scopeDenied();
    return api;
  }

  /** 同名字段由课程映射器复制，关系及primitive布尔值显式转换。 */
  private ResourcePermissionResult permissionResult(PermissionEntity permission) {
    ResourcePermissionResult result = CloneFactory.copy(new ResourcePermissionResult(), permission);
    result.setSystemId(permission.getSystem().getId());
    result.setParentId(permission.getParent() == null ? null : permission.getParent().getId());
    result.setEnabled(permission.isEnabled());
    return result;
  }

  /** 实体字段命名与已评审HTTP契约不同，方法/模板明确转换。 */
  private ApiResult apiResult(ApiResourceEntity api) {
    ApiResult result = CloneFactory.copy(new ApiResult(), api);
    result.setSystemId(api.getSystem().getId());
    result.setMethod(api.getHttpMethod());
    result.setPath(api.getPathTemplate());
    result.setEnabled(api.isEnabled());
    return result;
  }

  /** 映射按API主键排序，ids与items始终表达同一个完整集合。 */
  private PermissionApisResult mappingResult(PermissionEntity permission) {
    List<ApiResourceEntity> selected =
        permission.getApis().stream()
            .sorted(Comparator.comparing(ApiResourceEntity::getId))
            .toList();
    return new PermissionApisResult(
        selected.stream().map(ApiResourceEntity::getId).toList(),
        selected.stream().map(this::apiResult).toList());
  }

  /** 未知目标不泄漏表名或内部异常。 */
  private static BusinessException notFound() {
    return new BusinessException(404, "RESOURCE_NOT_FOUND", "资源不存在");
  }
}
