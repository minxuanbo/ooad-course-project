package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.user.UserWithRolesResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员行只返回契约字段，角色以ID集合整体表达。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(UserWithRolesResult.class)
public class UserWithRolesVO {
  private Long id;
  private String username;
  private String name;
  private Long departmentId;
  private boolean enabled;
  private List<Long> roleIds;
}
