package com.municipal.rbaccore.service.ddd.domain.model;

import com.municipal.rbaccore.service.ddd.domain.model.Permission.Type;
import java.util.*;

/** 应用接入配置聚合，维护树、映射及角色子集的完整一致性。 */
public record ApplicationOnboarding(
    ApplicationSystem application, Configuration configuration, List<RoleOption> roles) {
  /** 仓储定位使用领域键，不把HTTP参数类型直接作为仓储契约。 */
  public record Key(Long systemId) {}

  /** 当前事实以只读集合保留，调用者不能绕过聚合修改。 */
  public ApplicationOnboarding {
    roles = List.copyOf(roles);
  }

  /** 领域API事实使用自然键，持久化主键不暴露给导入文件。 */
  public record Api(String method, String path, Boolean enabled) {
    /** 同应用方法和路径组成稳定引用。 */
    public String key() {
      return method + " " + path;
    }
  }

  /** 父级只组织目录，所有实际API映射均显式指定。 */
  public record Permission(
      String code,
      String name,
      Type type,
      String parentCode,
      Boolean enabled,
      List<String> apiKeys) {}

  /** 复杂对象操作可通过显式元数据留给接入方判断。 */
  public record Operation(
      String operationId,
      String method,
      String path,
      Boolean requiresPermission,
      List<String> permissionRoles) {}

  /** 仅替换当前应用子集，不调整人员角色。 */
  public record Grant(Long roleId, List<String> permissionCodes) {}

  /** 范围与状态来自真实角色定义，不以展示名称授予资格。 */
  public record RoleOption(Long id, String code, String name, String type, Boolean enabled) {}

  /** 领域配置不含HTTP校验注解及持久化技术。 */
  public record Configuration(
      String entryUrl,
      String loginPermissionCode,
      List<Api> apis,
      List<Permission> permissions,
      List<Operation> operations,
      List<Grant> roleGrants) {}

  /** 对照当前聚合检查完整变更，已有身份不允许无痕删除。 */
  public List<String> problems(Configuration desired) {
    Configuration current = configuration;

    List<String> errors = new ArrayList<>();
    Map<String, Api> apiIndex = new LinkedHashMap<>();
    for (Api api : desired.apis())
      if (apiIndex.putIfAbsent(api.key(), api) != null) errors.add("API方法和路径重复：" + api.key());
    Map<String, Permission> permissionIndex = new LinkedHashMap<>();
    for (Permission permission : desired.permissions())
      if (permissionIndex.putIfAbsent(permission.code(), permission) != null)
        errors.add("权限编码重复：" + permission.code());
    for (Api api : current.apis())
      if (!apiIndex.containsKey(api.key())) errors.add("已有API须保留，可显式停用：" + api.key());
    for (Permission permission : current.permissions())
      if (!permissionIndex.containsKey(permission.code()))
        errors.add("已有权限须保留，可显式停用：" + permission.code());
    for (Permission permission : desired.permissions()) {
      if (new HashSet<>(permission.apiKeys()).size() != permission.apiKeys().size())
        errors.add("API映射重复：" + permission.code());
      for (String key : permission.apiKeys())
        if (!apiIndex.containsKey(key)) errors.add("API映射不属于当前应用草稿：" + key);
      if (permission.enabled()
          && permission.type() == Type.API
          && (permission.apiKeys().isEmpty()
              || permission.apiKeys().stream()
                  .anyMatch(key -> !apiIndex.containsKey(key) || !apiIndex.get(key).enabled())))
        errors.add("启用API权限须完整映射启用接口：" + permission.code());
      Set<String> visited = new HashSet<>();
      String cursor = permission.code();
      while (cursor != null && !cursor.isBlank()) {
        if (!visited.add(cursor)) {
          errors.add("权限树形成循环：" + permission.code());
          break;
        }
        Permission node = permissionIndex.get(cursor);
        if (node == null) {
          errors.add("父权限不在当前应用：" + cursor);
          break;
        }
        cursor = node.parentCode();
        if (cursor != null
            && !cursor.isBlank()
            && permissionIndex.containsKey(cursor)
            && permissionIndex.get(cursor).type() != Type.MENU) {
          errors.add("父权限必须是菜单：" + cursor);
          break;
        }
      }
    }
    Permission login = permissionIndex.get(desired.loginPermissionCode());
    if (login == null || !login.enabled() || login.type() == Type.API || !login.apiKeys().isEmpty())
      errors.add("请选择启用且不映射业务API的菜单或按钮作为独立登录资格");
    Set<String> operationIds = new HashSet<>();
    Set<String> roleCodes =
        roles.stream().map(RoleOption::code).collect(java.util.stream.Collectors.toSet());
    for (Operation operation : desired.operations()) {
      if (!operationIds.add(operation.operationId()))
        errors.add("operationId重复：" + operation.operationId());
      if (new HashSet<>(operation.permissionRoles()).size() != operation.permissionRoles().size()
          || !roleCodes.containsAll(operation.permissionRoles()))
        errors.add("操作条件角色不存在或重复：" + operation.operationId());
      if (operation.requiresPermission()) {
        String key = operation.method() + " " + operation.path();
        if (!apiIndex.containsKey(key)) errors.add("受控操作缺少本应用API：" + operation.operationId());
        else if (apiIndex.get(key).enabled()
            && desired.permissions().stream()
                .noneMatch(
                    permission -> permission.enabled() && permission.apiKeys().contains(key)))
          errors.add("受控操作缺少权限映射：" + operation.operationId());
      }
    }
    Set<Long> roleIds =
        roles.stream().map(RoleOption::id).collect(java.util.stream.Collectors.toSet());
    Set<Long> selected = new HashSet<>();
    boolean loginGranted = false;
    for (Grant grant : desired.roleGrants()) {
      if (!selected.add(grant.roleId()) || !roleIds.contains(grant.roleId()))
        errors.add("角色不存在或重复：" + grant.roleId());
      if (new HashSet<>(grant.permissionCodes()).size() != grant.permissionCodes().size()
          || !permissionIndex.keySet().containsAll(grant.permissionCodes()))
        errors.add("角色权限不属于本应用草稿或重复：" + grant.roleId());
      if (grant.permissionCodes().contains(desired.loginPermissionCode())
          && roles.stream().anyMatch(role -> role.id().equals(grant.roleId()) && role.enabled()))
        loginGranted = true;
    }
    if (!loginGranted) errors.add("至少一个启用角色需要被明确授予登录资格");
    return errors.stream().distinct().toList();
  }

  /** 仅在有效配置下转为已接入，不隐式恢复已停用应用。 */
  public ApplicationOnboarding connect(Configuration desired) {
    if (!application.enabled() || !problems(desired).isEmpty())
      throw new IllegalArgumentException("应用接入配置尚未完整");
    return new ApplicationOnboarding(
        application.revise(null, null, ApplicationSystem.Connection.CONNECTED), desired, roles);
  }
}
