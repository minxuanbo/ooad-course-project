package com.municipal.rbaccore.api.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 老师身份目录请求使用page从1开始、pageSize最多100，关键词不进入日志。 */
@Getter
@Setter
public class ApplicationDirectoryQueryDTO {
  @Min(1)
  private int page = 1;

  @Min(1)
  @Max(100)
  private int pageSize = 20;

  @Size(max = 100)
  private String keyword;
}
