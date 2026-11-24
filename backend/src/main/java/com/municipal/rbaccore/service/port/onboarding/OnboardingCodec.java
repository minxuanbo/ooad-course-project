package com.municipal.rbaccore.service.port.onboarding;

/** 技术性序列化摘要，不计算授权或组织策略。 */
public interface OnboardingCodec {
  /** 对完整当前内容生成稳定摘要，不包含它自身的旧摘要。 */
  String fingerprint(OnboardingPlan plan, java.util.List<String> facts);
}
