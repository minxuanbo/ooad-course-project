package com.municipal.rbaccore.api;

/** 对外整数ID采用已评审的JavaScript安全范围，持久化仍使用Long。 */
public final class CoreApiLimits {
  public static final long MAX_ID = 9007199254740991L;

  private CoreApiLimits() {}
}
