package com.municipal.rbaccore.service.port.onboarding;

import java.util.List;

/** 外部规范解析的技术端口，不下载引用或维护应用白名单。 */
public interface OpenApiReader {
  /** 返回默认需要授权的操作预览。 */
  List<OnboardingPlan.Operation> read(String document);
}
