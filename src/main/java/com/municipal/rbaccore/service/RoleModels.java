package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.Role;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 角色服务的命令及纯值结果，不携带实体、客户端权限或可信身份字段。 */
public interface RoleModels {
  /** 角色定义的安全表示，primitive状态从实体显式转换。 */
  @Getter
  @Setter
  @CopyFrom(Role.class)
  class RoleResult {
    private Long id;
    private String code;
    private String name;

    @CopyFrom.Exclude(Role.class)
    private Boolean enabled;
  }

  /** 相同筛选条件下的页内容及完整总数。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class PageResult {
    private List<RoleResult> items;
    private long total;
    private int page;
    private int size;
  }

  /** 已保存的直接权限，包括供清理的停用项，不补父级或子节点。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class PermissionsResult {
    private Long roleId;
    private List<Long> ids;
  }

  /** 请求校验后的分页、文字及状态条件。 */
  @Getter
  @Setter
  class Search {
    private String q;
    private Boolean enabled;
    private int page;
    private int size;
  }

  /** 创建后编码不可变，新角色默认启用且没有权限。 */
  @Getter
  @Setter
  class CreateCommand {
    private String code;
    private String name;
  }

  /** null只表示该字段缺省，输入中的显式null已在API拒绝。 */
  @Getter
  @Setter
  class PatchCommand {
    private String name;
    private Boolean enabled;
  }

  /** 角色直接权限的完整替换集合。 */
  @Getter
  @Setter
  class PermissionsCommand {
    private List<Long> ids;
  }
}
