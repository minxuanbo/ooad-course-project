package com.municipal.rbaccore.service.port.onboarding;

import java.util.List;

/** 两种实现各自执行接入配置业务，不由共享基础设施调用具体策略。 */
public interface OnboardingUseCase {
  /** 读取当前配置及并发指纹。 */
  OnboardingSnapshot snapshot(String header, Long id);

  /** 解析OpenAPI并只读预览，导入不会授予角色。 */
  List<OnboardingPlan.Operation> preview(String header, Long id, String document);

  /** 检查草稿，并在管理员选择时探测当前已发布身份协议。 */
  OnboardingReport check(
      String header, Long id, OnboardingPlan plan, String account, String password);

  /** 锁后重读及检查，确认对象权限和专用环境撤权后发布。 */
  OnboardingSnapshot complete(
      String header,
      Long id,
      OnboardingPlan plan,
      boolean businessConfirmed,
      boolean revocationConfirmed);

  /** MVC解析之前检查身份及实际API。 */
  void checkTarget(String header, Long id);
}
