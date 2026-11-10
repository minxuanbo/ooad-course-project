package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.CoreResults.UserSearch;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员查询参数的格式、范围和分页上限在API边界验证。 */
@Getter
@Setter
@NoArgsConstructor
@CopyTo(UserSearch.class)
public class UserQueryDTO {
  @Size(max = 100)
  private String q = "";

  @Positive private Long departmentId;

  @Pattern(regexp = "true|false")
  @CopyTo.Exclude(UserSearch.class)
  private String enabled;

  @Min(0)
  private int page;

  @Min(1)
  @Max(100)
  private int size = 20;
}
