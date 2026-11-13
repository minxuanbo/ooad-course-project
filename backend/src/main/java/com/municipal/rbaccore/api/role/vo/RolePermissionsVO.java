package com.municipal.rbaccore.api.role.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.role.RolePermissionsResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 按ID排序的完整直接权限集合。 */
@Getter
@Setter
@CopyFrom(RolePermissionsResult.class)
public class RolePermissionsVO {
  private Long roleId;
  private List<Long> ids;
}
