package com.municipal.rbaccore.config;

import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import org.springframework.core.env.Environment;

/** 部署秘密只在装配边界读取，不让用例输入依赖Spring环境。 */
public final class BootstrapCredentialsFactory {
  private BootstrapCredentialsFactory() {}

  /** 从部署环境读取必填口令；这里只给管理员用户名提供已确认的 admin 默认值。 */
  public static BootstrapCredentials from(Environment environment) {
    return new BootstrapCredentials(
        environment.getProperty("rbac.core.import.employee-password"),
        environment.getProperty("rbac.core.import.admin-password"),
        environment.getProperty("rbac.core.import.admin-username", "admin"));
  }
}
