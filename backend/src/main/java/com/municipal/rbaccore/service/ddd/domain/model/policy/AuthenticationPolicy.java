package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.User;
import java.util.List;
import java.util.Optional;

/** 两种登录标识不能从歧义结果任意选择一个账号。 */
public final class AuthenticationPolicy {
  private AuthenticationPolicy() {}

  /** 双入口必须唯一定位启用账号，密码验证由应用层调用技术端口。 */
  public static Optional<User> eligibleAccount(List<User> matches) {
    return matches.size() == 1 && matches.getFirst().enabled()
        ? Optional.of(matches.getFirst())
        : Optional.empty();
  }
}
