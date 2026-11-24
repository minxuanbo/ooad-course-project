package com.municipal.rbaccore.api.onboarding.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.onboarding.OnboardingReport;
import java.util.List;

/** 配置检查和真实协议测试分列展示，不把未启用标为通过。 */
public record OnboardingReportVO(
    boolean configurationValid, List<String> errors, List<CheckVO> protocolChecks) {
  /** 测试结果只含状态和说明，不返回测试令牌或凭据。 */
  @CopyFrom(OnboardingReport.Check.class)
  public record CheckVO(String code, String status, String message, Integer httpStatus) {}
}
