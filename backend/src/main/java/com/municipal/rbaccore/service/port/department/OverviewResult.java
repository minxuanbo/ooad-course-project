package com.municipal.rbaccore.service.port.department;

import com.municipal.rbaccore.service.port.user.DepartmentResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前授权范围的聚合，不包含范围外人员或全局日志。 */
@Getter
@Setter
@NoArgsConstructor
public class OverviewResult {
  private Long departmentId;
  private String departmentName;
  private long totalUsers;
  private long enabledUsers;
  private long disabledUsers;
  private long totalDepartments;
  private List<DepartmentResult> distribution;
}
