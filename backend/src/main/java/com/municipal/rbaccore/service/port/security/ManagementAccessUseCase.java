package com.municipal.rbaccore.service.port.security;

/** 管理预检与全局范围校验的共享用例。 */
public interface ManagementAccessUseCase {

  /** 读取当前操作并同时检查实际API和可信共同根。 */
  void require(String header);
}
