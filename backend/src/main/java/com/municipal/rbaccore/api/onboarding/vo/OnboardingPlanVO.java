package com.municipal.rbaccore.api.onboarding.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.onboarding.OnboardingPlan;
import java.util.List;

/** 向导返回模型与请求DTO分离，不泄漏持久化实体。 */
public record OnboardingPlanVO(
    String baseFingerprint,
    String entryUrl,
    String loginPermissionCode,
    List<ApiVO> apis,
    List<PermissionVO> permissions,
    List<OperationVO> operations,
    List<GrantVO> roleGrants) {
  /** 已登记或待发布接口的展示值。 */
  @CopyFrom(OnboardingPlan.Api.class)
  public record ApiVO(String method, String path, Boolean enabled) {}

  /** 同应用权限和显式关联。 */
  @CopyFrom(OnboardingPlan.Permission.class)
  public record PermissionVO(
      String code,
      String name,
      String type,
      String parentCode,
      Boolean enabled,
      List<String> apiKeys) {}

  /** 不携带运行期权限快照的操作元数据。 */
  @CopyFrom(OnboardingPlan.Operation.class)
  public record OperationVO(
      String operationId,
      String method,
      String path,
      Boolean requiresPermission,
      List<String> permissionRoles) {}

  /** 角色在当前应用下的权限子集。 */
  @CopyFrom(OnboardingPlan.Grant.class)
  public record GrantVO(Long roleId, List<String> permissionCodes) {}
}
