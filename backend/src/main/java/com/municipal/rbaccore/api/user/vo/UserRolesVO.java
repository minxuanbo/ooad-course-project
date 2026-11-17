package com.municipal.rbaccore.api.user.vo;

import com.municipal.rbaccore.api.role.vo.RoleVO;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
@Getter
@Setter
@NoArgsConstructor
public class UserRolesVO {
  private UserWithRolesVO user;

  private List<RoleVO> roles;
}
