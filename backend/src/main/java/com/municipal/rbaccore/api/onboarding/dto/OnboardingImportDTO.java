package com.municipal.rbaccore.api.onboarding.dto;

import jakarta.validation.constraints.*;

/** 只接收本次上传的规范文本，不接收供服务器访问的远端地址。 */
public record OnboardingImportDTO(
@NotBlank
@Size(max = 1048576)
String document) {}
