package com.municipal.rbaccore.service.port.onboarding;

import java.util.List;

/** 仅访问本RBAC真实HTTP协议，凭据只在本次调用内存中使用。 */
public interface IdentityProtocolProbe {
  /** 会话无论探测成功或失败均清理，不更改人员角色。 */
  List<OnboardingReport.Check> run(String system, String account, String password);
}
