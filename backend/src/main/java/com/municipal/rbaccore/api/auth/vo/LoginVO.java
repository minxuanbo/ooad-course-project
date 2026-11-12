package com.municipal.rbaccore.api.auth.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.auth.LoginResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 令牌格式和过期外形沿旧支撑；嵌套用户通过课程映射器转换。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(LoginResult.class)
public class LoginVO {
  private String token;

  @CopyFrom.Exclude(LoginResult.class)
  private IdentityVO user;

  private long expiresIn;
}
