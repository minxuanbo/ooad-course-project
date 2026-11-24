package com.municipal.rbaccore.service.port.onboarding;

import com.municipal.rbaccore.service.port.resource.SystemResult;
import java.util.List;

/** 可编辑快照与必要的角色选项，不携带人员或口令。 */
public record OnboardingSnapshot(SystemResult system, OnboardingPlan plan, List<RoleOption> roles) {
  /** 角色来源只用于管理员辨认，不按名字产生资格。 */
  public record RoleOption(Long id, String code, String name, String type, Boolean enabled) {}
}
