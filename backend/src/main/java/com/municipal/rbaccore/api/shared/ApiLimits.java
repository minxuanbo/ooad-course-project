package com.municipal.rbaccore.api.shared;

/** 对外整数ID采用已评审的JavaScript安全范围，持久化仍使用Long。 */
public final class ApiLimits {
  public static final long MAX_ID = 9007199254740991L;

  private ApiLimits() {}
}
