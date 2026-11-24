package com.municipal.rbaccore.service.port.onboarding;

import java.util.List;

/** 向导请求与快照的协议值，不承担组织范围或业务一致性策略。 */
public record OnboardingPlan(
    String baseFingerprint,
    String entryUrl,
    String loginPermissionCode,
    List<Api> apis,
    List<Permission> permissions,
    List<Operation> operations,
    List<Grant> roleGrants) {
  /** 仅替换并发比较标记，内容不变。 */
  public OnboardingPlan withFingerprint(String fingerprint) {
    return new OnboardingPlan(
        fingerprint, entryUrl, loginPermissionCode, apis, permissions, operations, roleGrants);
  }

  /** 一个稳定方法和路径标识的接口事实。 */
  public record Api(String method, String path, Boolean enabled) {
    /** 仅用于同应用草稿内引用，不是实际请求地址。 */
    public String key() {
      return method + " " + path;
    }
  }

  /** 权限编码稳定，父级和映射都用本应用自然键引用。 */
  public record Permission(
      String code,
      String name,
      String type,
      String parentCode,
      Boolean enabled,
      List<String> apiKeys) {}

  /** 操作元数据支持同应用内多个操作对应同一路由。 */
  public record Operation(
      String operationId,
      String method,
      String path,
      Boolean requiresPermission,
      List<String> permissionRoles) {}

  /** 本应用权限子集变更，不能借此改变人员角色或其他应用的权限。 */
  public record Grant(Long roleId, List<String> permissionCodes) {}
}
