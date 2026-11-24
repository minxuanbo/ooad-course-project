package com.municipal.rbaccore.api.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** 人工确认不能由自动连通性结果替代，仍须服务端锁后重验草稿。 */
public record OnboardingCompleteDTO(
    @NotNull
    @Valid
    OnboardingPlanDTO plan,
    @NotNull
    @AssertTrue
    Boolean businessConfirmed,
    @NotNull
    @AssertTrue
    Boolean revocationConfirmed) {}
