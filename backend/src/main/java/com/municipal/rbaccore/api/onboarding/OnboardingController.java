package com.municipal.rbaccore.api.onboarding;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.onboarding.dto.*;
import com.municipal.rbaccore.api.onboarding.vo.*;
import com.municipal.rbaccore.api.resource.vo.SystemVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.onboarding.*;
import com.municipal.rbaccore.service.port.security.Managed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 可视化向导的HTTP适配，关联一致性和锁后复核由两种业务实现负责。 */
@RestController
@RequestMapping("/api/systems/{id}/onboarding")
@Managed
@RequiredArgsConstructor
public class OnboardingController {
  private final OnboardingUseCase service;

  /** 打开向导时读取当前完整配置与并发标记。 */
  @GetMapping
  public OnboardingSnapshotVO get(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id) {
    return view(service.snapshot(header, id));
  }

  /** 上传规范只产生预览，不保存API或角色。 */
  @PostMapping("/import")
  public OnboardingImportVO preview(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id,
      @RequestBody
      @Valid
      OnboardingImportDTO dto) {
    return new OnboardingImportVO(
        service.preview(header, id, dto.document()).stream()
            .map(value -> CloneFactory.copy((OnboardingPlanVO.OperationVO) null, value))
            .toList());
  }

  /** 自动协议测试使用真实端点，待接入时明确返回待启用。 */
  @PostMapping("/check")
  public OnboardingReportVO check(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id,
      @RequestBody
      @Valid
      OnboardingCheckDTO dto) {
    OnboardingReport report =
        service.check(header, id, command(dto.plan()), dto.testAccount(), dto.testPassword());
    return new OnboardingReportVO(
        report.configurationValid(),
        report.errors(),
        report.protocolChecks().stream()
            .map(value -> CloneFactory.copy((OnboardingReportVO.CheckVO) null, value))
            .toList());
  }

  /** 完成会重新检查实际权限、目录及指纹，不信任客户端检查结论。 */
  @PostMapping("/complete")
  public OnboardingSnapshotVO complete(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id,
      @RequestBody
      @Valid
      OnboardingCompleteDTO dto) {
    return view(
        service.complete(
            header, id, command(dto.plan()), dto.businessConfirmed(), dto.revocationConfirmed()));
  }

  /** 嵌套集合元素类型不同，明确转换后组成共享命令。 */
  private OnboardingPlan command(OnboardingPlanDTO dto) {
    return new OnboardingPlan(
        dto.baseFingerprint(),
        dto.entryUrl(),
        dto.loginPermissionCode(),
        dto.apis().stream()
            .map(value -> CloneFactory.copy((OnboardingPlan.Api) null, value))
            .toList(),
        dto.permissions().stream()
            .map(value -> CloneFactory.copy((OnboardingPlan.Permission) null, value))
            .toList(),
        dto.operations().stream()
            .map(value -> CloneFactory.copy((OnboardingPlan.Operation) null, value))
            .toList(),
        dto.roleGrants().stream()
            .map(value -> CloneFactory.copy((OnboardingPlan.Grant) null, value))
            .toList());
  }

  /** 集合元素映射使用真实0.0.2构造器映射，外层不同类型显式组装。 */
  private OnboardingSnapshotVO view(OnboardingSnapshot snapshot) {
    OnboardingPlan source = snapshot.plan();
    OnboardingPlanVO plan =
        new OnboardingPlanVO(
            source.baseFingerprint(),
            source.entryUrl(),
            source.loginPermissionCode(),
            source.apis().stream()
                .map(value -> CloneFactory.copy((OnboardingPlanVO.ApiVO) null, value))
                .toList(),
            source.permissions().stream()
                .map(value -> CloneFactory.copy((OnboardingPlanVO.PermissionVO) null, value))
                .toList(),
            source.operations().stream()
                .map(value -> CloneFactory.copy((OnboardingPlanVO.OperationVO) null, value))
                .toList(),
            source.roleGrants().stream()
                .map(value -> CloneFactory.copy((OnboardingPlanVO.GrantVO) null, value))
                .toList());
    return new OnboardingSnapshotVO(
        CloneFactory.copy(new SystemVO(), snapshot.system()),
        plan,
        snapshot.roles().stream()
            .map(value -> CloneFactory.copy((OnboardingSnapshotVO.RoleVO) null, value))
            .toList());
  }
}
