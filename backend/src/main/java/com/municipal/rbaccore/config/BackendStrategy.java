package com.municipal.rbaccore.config;

import org.springframework.core.env.Environment;

/** 三种启动入口共用策略取值，配置错误应早于数据源连接。 */
enum BackendStrategy {
  STRUCTURED,
  DDD;

  /** 空白、拼写错误和其他取值都必须显式失败。 */
  static BackendStrategy from(Environment environment) {
    String value = environment.getProperty("app.backend-strategy", "structured");
    return switch (value) {
      case "structured" -> STRUCTURED;
      case "ddd" -> DDD;
      default -> throw new IllegalArgumentException("app.backend-strategy 无效：" + value);
    };
  }
}
