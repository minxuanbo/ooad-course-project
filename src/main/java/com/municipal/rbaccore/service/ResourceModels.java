package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.*;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 资源服务命令及纯值结果，实体关系只在事务内部展开。 */
public interface ResourceModels {
  /** 当前系统的安全目录表示。 */
  @Getter
  @Setter
  @CopyFrom(ApplicationSystem.class)
  class SystemResult {
    private Long id;
    private String code;
    private String name;

    @CopyFrom.Exclude(ApplicationSystem.class)
    private Boolean enabled;
  }

  /** 树的父引用与children为展示投影，不携带JPA关联。 */
  @Getter
  @Setter
  @CopyFrom(Permission.class)
  class PermissionResult {
    private Long id;
    private Long systemId;
    private Long parentId;
    private Permission.Type type;
    private String code;
    private String name;

    @CopyFrom.Exclude(Permission.class)
    private Boolean enabled;

    @CopyFrom.Exclude(Permission.class)
    private List<PermissionResult> children;
  }

  /** method/path按契约命名，和实体httpMethod/pathTemplate显式转换。 */
  @Getter
  @Setter
  @CopyFrom(ApiResource.class)
  class ApiResult {
    private Long id;
    private Long systemId;
    private String method;
    private String path;

    @CopyFrom.Exclude(ApiResource.class)
    private Boolean enabled;
  }

  /** 页码、大小和total不随当前页内容变化。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class ApiPageResult {
    private List<ApiResult> items;
    private long total;
    private int page;
    private int size;
  }

  /** 映射真值ids包含所有已保存项，含尚未解除的停用API。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class MappingResult {
    private List<Long> ids;
    private List<ApiResult> items;
  }

  /** 通过API格式校验后的目录查询命令。 */
  @Getter
  @Setter
  class ApiSearch {
    private Long systemId;
    private String q;
    private int page;
    private int size;
  }

  /** 权限PATCH的字段存在性单独表达，不能用null推断缺省。 */
  @Getter
  @Setter
  class PermissionPatch {
    private Long parentId;
    private Boolean parentIdPresent;
    private Permission.Type type;
    private Boolean enabled;
  }

  /** API启停命令，类型转换后的状态由受控业务入口更新。 */
  @Getter
  @Setter
  class ApiPatch {
    private Boolean enabled;
  }

  /** 全组映射替换，不包含前端权限或可信身份字段。 */
  @Getter
  @Setter
  class MappingCommand {
    private List<Long> ids;
  }
}
