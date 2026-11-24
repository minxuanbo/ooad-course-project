package com.municipal.rbaccore.api.onboarding.vo;

import java.util.List;

/** 预览解析结果不代表目录已保存。 */
public record OnboardingImportVO(List<OnboardingPlanVO.OperationVO> operations) {}
