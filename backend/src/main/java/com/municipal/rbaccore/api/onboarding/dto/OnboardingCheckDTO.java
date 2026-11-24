package com.municipal.rbaccore.api.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** 测试凭据仅供本次自调用，服务和数据库不保存它们。 */
public record OnboardingCheckDTO(
    @NotNull
    @Valid
    OnboardingPlanDTO plan,
    @Size(max = 100)
    String testAccount,
    @Size(max = 100)
    String testPassword) {
  /** 两项同时填写才发起真实协议探测，空白口令不猜测为默认密码。 */
  @AssertTrue(message = "测试账号和密码需要同时填写")
  @JsonIgnore
  public boolean isCredentialPairValid() {
    return (testAccount == null || testAccount.isBlank())
        == (testPassword == null || testPassword.isBlank());
  }
}
