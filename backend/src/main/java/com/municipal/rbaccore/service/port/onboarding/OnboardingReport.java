package com.municipal.rbaccore.service.port.onboarding;

import java.util.List;

/** 分开表达配置检查、真实协议探测及需要人工确认的业务测试。 */
public record OnboardingReport(
    boolean configurationValid, List<String> errors, List<Check> protocolChecks) {
  /** 状态采用PASS/FAIL/PENDING，PENDING不得展示为成功。 */
  public record Check(String code, String status, String message, Integer httpStatus) {}
}
