package com.municipal.rbaccore.api.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.service.CoreResults;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HTTP输出VO只表达经评审契约，不直接序列化实体。 */
public interface CoreViews {
  /** 工号即用户名，不返回重复工号、手机号或哈希。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.IdentityResult.class)
  class IdentityVO {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;
    private boolean enabled;
  }

  /** 本人身份保持user一层外形。 */
  @Getter
  @AllArgsConstructor
  class MeVO {
    private final IdentityVO user;
  }

  /** 令牌格式和过期外形沿旧支撑；嵌套用户通过课程映射器转换。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.LoginResult.class)
  class LoginVO {
    private String token;

    @CopyFrom.Exclude(CoreResults.LoginResult.class)
    private IdentityVO user;

    private long expiresIn;
  }

  /** 人员行不携带敏感字段或JPA关系。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.UserResult.class)
  class UserVO {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;
    private String departmentName;
    private boolean enabled;
  }

  /** 人员分页与已有page、size、total契约一致。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.UserPageResult.class)
  class UserPageVO {
    @CopyFrom.Exclude(CoreResults.UserPageResult.class)
    private List<UserVO> items;

    private long total;
    private int page;
    private int size;
  }

  /** 本系统有效权限，不混入Excel其他系统的条目。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.PermissionResult.class)
  class PermissionVO {
    private Long id;
    private Long systemId;
    private Long parentId;
    private Permission.Type type;
    private String code;
    private String name;
  }

  /** 客户端按parentId构树，可见根不暴露范围外父节点。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(CoreResults.DepartmentResult.class)
  class DepartmentVO {
    private Long id;
    private Long parentId;
    private String name;
  }
}
