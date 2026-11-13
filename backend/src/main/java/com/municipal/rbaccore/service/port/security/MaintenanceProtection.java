package com.municipal.rbaccore.service.port.security;

/** 同一根锁与完整维护者保护的共享业务支撑，不暴露持久化锁对象。 */
public interface MaintenanceProtection {
  /** 在调用方事务内取得可信共同根锁。 */
  void lock();

  /** 检查至少保留同一用户完整覆盖K的维护者。 */
  void retainOne();
}
