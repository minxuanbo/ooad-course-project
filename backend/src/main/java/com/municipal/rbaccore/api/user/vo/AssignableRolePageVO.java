package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.api.role.vo.RoleVO;
import com.municipal.rbaccore.service.port.user.AssignableRolePageResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 候选角色分页，元素只包含启用且属于本人有效权限子集的角色。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(AssignableRolePageResult.class)
public class AssignableRolePageVO {
  @CopyFrom.Exclude(AssignableRolePageResult.class)
  private List<RoleVO> items;

  private long total;
  private int page;
  private int size;
}
