package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.api.role.vo.RoleVO;
import com.municipal.rbaccore.service.port.user.UserRolesResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(UserRolesResult.class)
public class UserRolesVO {
  @CopyFrom.Exclude(UserRolesResult.class)
  private UserWithRolesVO user;

  @CopyFrom.Exclude(UserRolesResult.class)
  private List<RoleVO> roles;
}
