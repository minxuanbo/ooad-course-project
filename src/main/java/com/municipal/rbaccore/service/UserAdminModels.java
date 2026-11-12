package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.User;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员管理服务的命令及纯值结果，不携带实体、客户端权限或可信身份字段。 */
public interface UserAdminModels {
  /** 已保存角色集合的安全表示，roleIds为整体替换后的完整关联。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(User.class)
  class UserWithRolesResult {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;

    @CopyFrom.Exclude(User.class)
    private Boolean enabled;

    @CopyFrom.Exclude(User.class)
    private List<Long> roleIds;
  }

  /** 当前人员与完整角色元数据，角色可包含停用或现已不可分配项。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class RolesViewResult {
    private UserWithRolesResult user;
    private List<RoleModels.RoleResult> roles;
  }

  /** 相同筛选条件下的候选角色页内容及完整总数。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class AssignablePageResult {
    private List<RoleModels.RoleResult> items;
    private long total;
    private int page;
    private int size;
  }

  /** 候选角色的名称/编码搜索与分页条件。 */
  @Getter
  @Setter
  class AssignableSearch {
    private String q;
    private int page;
    private int size;
  }

  /** 人员角色的完整替换集合，空数组表示清空全部关联。 */
  @Getter
  @Setter
  class ReplaceRolesCommand {
    private List<Long> ids;
  }

  /** 本票仅接受停用指令，不提供恢复入口。 */
  @Getter
  @Setter
  class DisableCommand {
    private Boolean enabled;
  }

  /** 调动目标部门为单一替换值。 */
  @Getter
  @Setter
  class MoveCommand {
    private Long departmentId;
  }
}
