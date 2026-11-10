package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.model.User;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 服务层返回模型不持有JPA实体；非final字段兼容课程CloneFactory的字段发现规则。 */
public interface CoreResults {
  /** 当前身份只返回契约中的安全字段。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(User.class)
  class IdentityResult {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;

    @CopyFrom.Exclude(User.class)
    private Boolean enabled;
  }

  /** 人员列表输出不包含手机号、口令哈希或岗位原始资料。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(User.class)
  class UserResult {
    private Long id;
    private String username;
    private String name;
    private Long departmentId;
    private String departmentName;

    @CopyFrom.Exclude(User.class)
    private Boolean enabled;
  }

  /** 本人实际有效权限，不扩展父级导航的授权含义。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(Permission.class)
  class PermissionResult {
    private Long id;
    private Long systemId;
    private Long parentId;
    private Permission.Type type;
    private String code;
    private String name;
  }

  /** 部门导航的可见根由服务投影为null，实体父引用不变。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(Department.class)
  class DepartmentResult {
    private Long id;
    private Long parentId;
    private String name;
  }

  /** 登录沿用既有token、user、expiresIn响应外形。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class LoginResult {
    private String token;
    private IdentityResult user;
    private long expiresIn;
  }

  /** 分页总数来自与查询一致的数据库谓词。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class UserPageResult {
    private List<UserResult> items;
    private long total;
    private int page;
    private int size;
  }

  /** 查询命令与HTTP DTO分离，跨字段派生值由Controller转换。 */
  @Getter
  @Setter
  @NoArgsConstructor
  class UserSearch {
    private String q;
    private Long departmentId;
    private Boolean enabled;
    private int page;
    private int size;
  }
}
