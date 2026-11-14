package com.municipal.rbaccore.api.department.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.api.user.vo.DepartmentVO;
import com.municipal.rbaccore.service.port.department.OverviewResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 总览安全输出，分布节点在Controller完成类型转换。 */
@Getter
@Setter
@CopyFrom(OverviewResult.class)
public class OverviewVO {
  private Long departmentId;
  private String departmentName;
  private long totalUsers;
  private long enabledUsers;
  private long disabledUsers;
  private long totalDepartments;

  @CopyFrom.Exclude(OverviewResult.class)
  private List<DepartmentVO> distribution;
}
