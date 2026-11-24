package com.municipal.rbaccore.api.onboarding.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.api.resource.vo.SystemVO;
import com.municipal.rbaccore.service.port.onboarding.OnboardingSnapshot;
import java.util.List;

/** 当前应用、草稿初值及角色选项。 */
public record OnboardingSnapshotVO(SystemVO system, OnboardingPlanVO plan, List<RoleVO> roles) {
  /** 仅显示角色定义，不附带人员名单。 */
  @CopyFrom(OnboardingSnapshot.RoleOption.class)
  public record RoleVO(Long id, String code, String name, String type, Boolean enabled) {}
}
