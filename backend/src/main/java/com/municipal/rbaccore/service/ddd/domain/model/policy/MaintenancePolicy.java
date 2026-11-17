package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import java.util.Set;

/** 最低维护能力必须由一个符合根和级别条件的账号单独覆盖。 */
public final class MaintenancePolicy {
  private MaintenancePolicy() {}

  /** 跨不同用户的授权并集不能代替单个完整维护者。 */
  public static boolean qualifies(
      AuthorizationFacts facts, Long root, Set<ApiResource.Route> required) {
    User user = facts.user();
    return user.enabled()
        && root.equals(user.department().id())
        && user.level() == Role.ADMIN_LEVEL
        && AuthorizationPolicy.routes(facts).containsAll(required);
  }
}
