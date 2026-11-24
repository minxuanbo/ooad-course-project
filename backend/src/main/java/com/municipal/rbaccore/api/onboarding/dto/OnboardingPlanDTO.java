package com.municipal.rbaccore.api.onboarding.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.municipal.rbaccore.service.port.onboarding.OnboardingPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;

/** 向导输入形状和字段边界在API层检查，不把不可信关联当作授权结果。 */
public record OnboardingPlanDTO(
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{64}")
    String baseFingerprint,
    @Size(max = 512)
    String entryUrl,
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*")
        String loginPermissionCode,
    @NotNull
    @Size(max = 2000)
    @Valid
    List<
        @NotNull
        ApiDTO> apis,
    @NotNull
    @Size(min = 1, max = 2000)
    @Valid
    List<
        @NotNull
        PermissionDTO> permissions,
    @NotNull
    @Size(max = 2000)
    @Valid
    List<
        @NotNull
        OperationDTO> operations,
    @NotNull
    @Size(max = 2000)
    @Valid
    List<
        @NotNull
        GrantDTO> roleGrants) {
  /** 入口地址只用于展示，不允许凭据或片段，也不由后端抓取该地址。 */
  @AssertTrue(message = "入口地址须为不含凭据的HTTP或HTTPS地址")
  @JsonIgnore
  public boolean isEntryUrlValid() {
    if (entryUrl == null || entryUrl.isBlank()) return true;
    try {
      URI uri = URI.create(entryUrl);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && uri.getFragment() == null;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  /** 单个API的格式，唯一性与同应用引用由业务服务检查。 */
  @CopyTo(OnboardingPlan.Api.class)
  public record ApiDTO(
      @NotBlank
      @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
      String method,
      @NotBlank
      @Size(max = 512)
      @Pattern(regexp = "/[^\\s?#]*")
      String path,
      @NotNull
      Boolean enabled) {}

  /** 菜单按钮均独立选择映射，父级不代表继承。 */
  @CopyTo(OnboardingPlan.Permission.class)
  public record PermissionDTO(
      @NotBlank
      @Size(max = 100)
      @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*")
      String code,
      @NotBlank
      @Size(max = 191)
      String name,
      @NotBlank
      @Pattern(regexp = "MENU|BUTTON|API")
      String type,
      @Size(max = 100)
      String parentCode,
      @NotNull
      Boolean enabled,
      @NotNull
      @Size(max = 2000)
      List<
          @NotBlank
          @Size(max = 520)
          String> apiKeys) {}

  /** operationId的条件角色仍为显式配置，不按教师或学生写死规则。 */
  @CopyTo(OnboardingPlan.Operation.class)
  public record OperationDTO(
      @NotBlank
      @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}")
      String operationId,
      @NotBlank
      @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
      String method,
      @NotBlank
      @Size(max = 512)
      @Pattern(regexp = "/[^\\s?#]*")
      String path,
      @NotNull
      Boolean requiresPermission,
      @NotNull
      @Size(max = 2000)
          List<
              @NotBlank
              @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")
              String>
              permissionRoles) {}

  /** 只能提交本应用权限编码，其他应用关联不属于请求体。 */
  @CopyTo(OnboardingPlan.Grant.class)
  public record GrantDTO(
      @NotNull
      @Positive
      @Max(9007199254740991L)
      Long roleId,
      @NotNull
      @Size(max = 2000)
      List<
          @NotBlank
          @Size(max = 100)
          String> permissionCodes) {}
}
