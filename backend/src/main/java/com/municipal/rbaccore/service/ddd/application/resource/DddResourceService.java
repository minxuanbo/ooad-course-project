package com.municipal.rbaccore.service.ddd.application.resource;

import com.municipal.rbaccore.service.ddd.application.support.DddWrite;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.PermissionTreePolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.*;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 读取和修改本RBAC系统的权限、API与直接映射。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddResourceService implements ResourceUseCase {
  private final ApplicationSystemRepository systems;
  private final PermissionRepository permissions;
  private final ApiResourceRepository apis;
  private final ManagementAccessUseCase access;
  private final DddWrite writes;

  /** 维护目录的分页投影由仓储返回，授权仍在应用用例独立执行。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SystemPageResult searchSystems(String header, SystemQuery query) {
    access.require(header);
    DomainPage<ApplicationSystem> page =
        systems.search(
            new SearchCriteria.Systems(
                query.getQ(), query.getEnabled(), query.getPage(), query.getSize()));
    return new SystemPageResult(
        page.items().stream().map(this::systemResult).toList(),
        page.total(),
        page.page(),
        page.size());
  }

  /** 新登记默认待接入，业务权限和角色不由应用登记隐式创建。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SystemResult createSystem(String header, CreateSystemCommand command) {
    return writes.global(
        header,
        () ->
            systemResult(
                systems.save(
                    new ApplicationSystem(
                        null,
                        command.getCode(),
                        command.getName().trim(),
                        true,
                        ApplicationSystem.Connection.PENDING))));
  }

  /** 领域值表达生命周期变更，仓储只负责保存同一目录事实。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SystemResult patchSystem(String header, Long id, UpdateSystemCommand command) {
    return writes.global(
        header,
        () ->
            systemResult(
                systems.save(
                    selectSystem(id)
                        .revise(
                            command.getName(),
                            command.getEnabled(),
                            command.getConnectionStatus() == null
                                ? null
                                : ApplicationSystem.Connection.valueOf(
                                    command.getConnectionStatus().name())))));
  }

  /** 移除仅关闭应用可用性，引用、ID和日志仍保留。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SystemResult removeSystem(String header, Long id) {
    return writes.global(
        header, () -> systemResult(systems.save(selectSystem(id).revise(null, false, null))));
  }

  /** 不为字段映射让共享port依赖DDD，领域枚举及结果明确转换。 */
  private SystemResult systemResult(ApplicationSystem system) {
    SystemResult result = new SystemResult();
    result.setId(system.id());
    result.setCode(system.code());
    result.setName(system.name());
    result.setEnabled(system.enabled());
    result.setConnectionStatus(ConnectionStatus.valueOf(system.connectionStatus().name()));
    return result;
  }

  /** 按登记事实返回全部应用目录，保留停用项供维护。 */
  @Override
  public List<SystemResult> systems(String header) {
    access.require(header);
    selectSystem(null);
    return systems.catalog().stream()
        .map(this::systemResult)
        .toList();
  }

  /** 按稳定ID组装父子层级，停用节点仍出现在维护目录。 */
  @Override
  public List<ResourcePermissionResult> tree(String header, Long systemId) {
    access.require(header);
    Map<Long, ResourcePermissionResult> nodes = new LinkedHashMap<>();
    for (Permission permission : permissions.tree(selectSystem(systemId))) {
      ResourcePermissionResult result = result(permission);
      result.setChildren(new ArrayList<>());
      nodes.put(permission.id(), result);
    }
    List<ResourcePermissionResult> roots = new ArrayList<>();
    for (ResourcePermissionResult node : nodes.values()) {
      if (node.getParentId() == null) roots.add(node);
      else {
        ResourcePermissionResult parent = nodes.get(node.getParentId());
        if (parent == null) throw new BusinessException(409, "DATA_CONFLICT", "权限树父级不在当前系统");
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  /** API条目及总数使用同一系统和筛选快照。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ApiPageResult apis(String header, ApiQuery query) {
    access.require(header);
    DomainPage<ApiResource> page =
        apis.search(
            new SearchCriteria.Apis(
                selectSystem(query.getSystemId()), query.getQ(), query.getPage(), query.getSize()));
    return new ApiPageResult(
        page.items().stream().map(this::result).toList(), page.total(), page.page(), page.size());
  }

  /** 已保存映射完整返回，启停另行判定。 */
  @Override
  public PermissionApisResult mapping(String header, Long permissionId) {
    access.require(header);
    return mapping(permission(permissionId));
  }

  /** 权限编号在请求参数解析前执行作用域预检。 */
  @Override
  public void checkPermissionTarget(String header, Long id) {
    access.require(header);
    permission(id);
  }

  /** API编号在请求参数解析前执行作用域预检。 */
  @Override
  public void checkApiTarget(String header, Long id) {
    access.require(header);
    api(id);
  }

  /** 系统编号不泄漏外系统存在性。 */
  @Override
  public void checkSystemTarget(String header, Long id) {
    access.require(header);
    selectSystem(id);
  }

  /** 映射一次整体保存，由领域核实完整同系统API集合。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PermissionApisResult replaceMapping(
      String header, Long id, ReplacePermissionApisCommand command) {
    return writes.global(
        header,
        () -> {
          Permission current = permission(id);
          Set<ApiResource.Key> keys =
              command.getIds().stream()
                  .map(ApiResource.Key::new)
                  .collect(Collectors.toUnmodifiableSet());
          Permission changed =
              PermissionTreePolicy.map(current, apis.select(keys), command.getIds().size());
          return mapping(permissions.save(changed));
        });
  }

  /** 显式null父级代表移到根层；缺省保留原值。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ResourcePermissionResult patchPermission(
      String header, Long id, UpdatePermissionCommand command) {
    return writes.global(
        header,
        () -> {
          Permission current = permission(id);
          boolean parentPresent = Boolean.TRUE.equals(command.getParentIdPresent());
          Long parent = parentPresent ? command.getParentId() : current.parentId();
          Permission.Type type =
              command.getType() == null ? null : Permission.Type.valueOf(command.getType().name());
          Permission changed;
          if (parentPresent || type != null) {
            Map<Long, Permission> tree =
                permissions.tree(current.system()).stream()
                    .collect(Collectors.toMap(Permission::id, Function.identity()));
            changed =
                PermissionTreePolicy.revise(current, tree, parent, type, command.getEnabled());
          } else {
            changed = current.revise(parent, null, null, command.getEnabled());
          }
          return result(permissions.save(changed));
        });
  }

  /** API启停后同事务复核K，既有映射不变。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ApiResult patchApi(String header, Long id, UpdateApiCommand command) {
    return writes.global(
        header, () -> result(apis.save(api(id).changeAvailability(command.getEnabled()))));
  }

  /** 缺省定位RBAC；显式编号必须存在于登记目录。 */
  private ApplicationSystem selectSystem(Long requested) {
    if (requested == null) return systems.findRbac().orElseThrow(this::notFound);
    return systems.catalog().stream()
        .filter(system -> requested.equals(system.id()))
        .findFirst()
        .orElseThrow(BusinessException::scopeDenied);
  }

  /** 已授权操作的未知资源返回404，并核实其所属登记应用。 */
  private Permission permission(Long id) {
    Permission value = permissions.find(new Permission.Key(id)).orElseThrow(this::notFound);
    selectSystem(value.system().id());
    return value;
  }

  /** API直接目标同样执行所属系统保护。 */
  private ApiResource api(Long id) {
    ApiResource value = apis.find(new ApiResource.Key(id)).orElseThrow(this::notFound);
    selectSystem(value.system().id());
    return value;
  }

  /** 不可变领域权限显式转换为公共结果。 */
  private ResourcePermissionResult result(Permission value) {
    ResourcePermissionResult result = new ResourcePermissionResult();
    result.setId(value.id());
    result.setSystemId(value.system().id());
    result.setParentId(value.parentId());
    result.setCode(value.code());
    result.setName(value.name());
    result.setType(PermissionType.valueOf(value.type().name()));
    result.setEnabled(value.enabled());
    return result;
  }

  /** API公开字段使用契约方法和路径名称。 */
  private ApiResult result(ApiResource value) {
    ApiResult result = new ApiResult();
    result.setId(value.id());
    result.setSystemId(value.system().id());
    result.setMethod(value.route().method());
    result.setPath(value.route().path());
    result.setEnabled(value.enabled());
    return result;
  }

  /** 编号及条目按同一排序表达完整映射。 */
  private PermissionApisResult mapping(Permission permission) {
    List<ApiResource> ordered =
        permission.apis().stream().sorted(Comparator.comparing(ApiResource::id)).toList();
    return new PermissionApisResult(
        ordered.stream().map(ApiResource::id).toList(),
        ordered.stream().map(this::result).toList());
  }

  /** 统一可见资源的不存在错误。 */
  private BusinessException notFound() {
    return new BusinessException(404, "RESOURCE_NOT_FOUND", "资源不存在");
  }
}
