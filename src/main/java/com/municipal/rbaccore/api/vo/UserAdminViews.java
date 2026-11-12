package com.municipal.rbaccore.api.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.UserAdminModels;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** T10人员角色、停用与调动的HTTP输出，不直接序列化实体。 */
public interface UserAdminViews {
  /** 人员行只返回契约字段，角色以ID集合整体表达。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(UserAdminModels.UserWithRolesResult.class)
  class UserWithRolesVO {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;
    private boolean enabled;
    private List<Long> roleIds;
  }

  /** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(UserAdminModels.RolesViewResult.class)
  class RolesViewVO {
    @CopyFrom.Exclude(UserAdminModels.RolesViewResult.class)
    private UserWithRolesVO user;

    @CopyFrom.Exclude(UserAdminModels.RolesViewResult.class)
    private List<RoleViews.RoleVO> roles;
  }

  /** 候选角色分页，元素只包含启用且属于本人有效权限子集的角色。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(UserAdminModels.AssignablePageResult.class)
  class AssignablePageVO {
    @CopyFrom.Exclude(UserAdminModels.AssignablePageResult.class)
    private List<RoleViews.RoleVO> items;

    private long total;
    private int page;
    private int size;
  }
}
