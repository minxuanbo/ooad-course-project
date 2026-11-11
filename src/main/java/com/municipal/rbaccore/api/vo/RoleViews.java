package com.municipal.rbaccore.api.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.RoleModels;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 角色HTTP响应使用独立VO，关系集合不交给Jackson隐式展开。 */
public interface RoleViews {
  /** 必要角色定义，不增加部门、继承或管理员标记。 */
  @Getter
  @Setter
  @CopyFrom(RoleModels.RoleResult.class)
  class RoleVO {
    private Long id;
    private String code;
    private String name;
    private Boolean enabled;
  }

  /** 分页元信息由课程映射器复制，元素类型显式转为VO。 */
  @Getter
  @Setter
  @CopyFrom(RoleModels.PageResult.class)
  class PageVO {
    @CopyFrom.Exclude(RoleModels.PageResult.class)
    private List<RoleVO> items;

    private long total;
    private int page;
    private int size;
  }

  /** 按ID排序的完整直接权限集合。 */
  @Getter
  @Setter
  @CopyFrom(RoleModels.PermissionsResult.class)
  class PermissionsVO {
    private Long roleId;
    private List<Long> ids;
  }
}
