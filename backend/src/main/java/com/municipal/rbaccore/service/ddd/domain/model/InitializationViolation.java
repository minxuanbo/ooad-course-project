package com.municipal.rbaccore.service.ddd.domain.model;

/** 初始化的领域冲突，以原因区别缺失、变化和非法目标。 */
public final class InitializationViolation extends RuntimeException {
  private final String code;

  /** 初始化离线原因由应用层翻译成既有命令契约。 */
  public InitializationViolation(String code, String message) {
    super(message);
    this.code = code;
  }

  /** 返回离线业务错误标识。 */
  public String code() {
    return code;
  }
}
