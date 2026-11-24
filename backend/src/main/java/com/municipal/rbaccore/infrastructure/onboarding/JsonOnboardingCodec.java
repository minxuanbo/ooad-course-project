package com.municipal.rbaccore.infrastructure.onboarding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.service.port.onboarding.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 仅计算协议内容摘要，用于识别跨页面的并发配置变化。 */
@Component
@RequiredArgsConstructor
public class JsonOnboardingCodec implements OnboardingCodec {
  private final ObjectMapper mapper;

  /** 配置和当前事实共同组成无歧义的摘要输入。 */
  private record Content(OnboardingPlan plan, java.util.List<String> facts) {}

  /** 不将凭据放入计划，摘要不代表权限快照或授权缓存。 */
  @Override
  public String fingerprint(OnboardingPlan plan, java.util.List<String> facts) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsBytes(new Content(plan.withFingerprint(""), facts))));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("接入内容摘要失败", exception);
    }
  }
}
