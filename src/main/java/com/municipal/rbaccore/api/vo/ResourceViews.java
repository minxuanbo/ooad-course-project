package com.municipal.rbaccore.api.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.service.ResourceModels;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 资源HTTP结果采用独立VO，禁止序列化实体或持久化集合。 */
public interface ResourceViews {
  /** 仅当前RBAC系统，不暴露未评审的其他系统目录。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.SystemResult.class)
  class SystemVO {
    private Long id;
    private String code;
    private String name;
    private Boolean enabled;
  }

  /** 单节点PATCH返回平面字段，树查询再组合children。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.PermissionResult.class)
  class PermissionVO {
    private Long id;
    private Long systemId;
    private Long parentId;
    private Permission.Type type;
    private String code;
    private String name;
    private Boolean enabled;
  }

  /** 树响应的子元素也使用VO，避免结果与实体形成反向循环。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.PermissionResult.class)
  class PermissionTreeVO {
    private Long id;
    private Long systemId;
    private Long parentId;
    private Permission.Type type;
    private String code;
    private String name;
    private Boolean enabled;

    @CopyFrom.Exclude(ResourceModels.PermissionResult.class)
    private List<PermissionTreeVO> children;
  }

  /** API只读方法模板，enabled允许必要启停。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.ApiResult.class)
  class ApiVO {
    private Long id;
    private Long systemId;
    private String method;
    private String path;
    private Boolean enabled;
  }

  /** 有增长可能的API目录保持分页响应。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.ApiPageResult.class)
  class ApiPageVO {
    @CopyFrom.Exclude(ResourceModels.ApiPageResult.class)
    private List<ApiVO> items;

    private long total;
    private int page;
    private int size;
  }

  /** 完整已保存映射不受候选列表的搜索或分页截断。 */
  @Getter
  @Setter
  @CopyFrom(ResourceModels.MappingResult.class)
  class MappingVO {
    private List<Long> ids;

    @CopyFrom.Exclude(ResourceModels.MappingResult.class)
    private List<ApiVO> items;
  }
}
