package com.municipal.rbaccore.service.port.security;

import java.util.Set;

/** 由装配层提供的显式系统目录，不包含组织或授权计算策略。 */
public record ManagedApplications(Set<String> codes) {
  /** 保存不可变目录，防止请求处理中被修改。 */
  public ManagedApplications {
    codes = Set.copyOf(codes);
  }
}
