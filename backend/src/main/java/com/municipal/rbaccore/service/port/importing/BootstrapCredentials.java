package com.municipal.rbaccore.service.port.importing;

import com.municipal.rbaccore.service.port.error.ImportException;
import java.nio.charset.StandardCharsets;
import lombok.Getter;

/** 新项目的初始化输入，不生成 toString，口令只用于哈希而不进入来源记录。 */
@Getter
public final class BootstrapCredentials {
  private final String employeePassword;
  private final String adminPassword;
  private final String adminUsername;

  /** 员工和管理员分别配置口令，禁止静默使用旧项目默认值。 */
  public BootstrapCredentials(String employeePassword, String adminPassword, String adminUsername) {
    requirePassword(employeePassword);
    requirePassword(adminPassword);
    if (adminUsername == null || adminUsername.isBlank() || adminUsername.trim().length() > 100) {
      throw new ImportException("BOOTSTRAP_CONFIG_INVALID", "必须配置不超过100字符的管理员账号名");
    }
    this.employeePassword = employeePassword;
    this.adminPassword = adminPassword;
    this.adminUsername = adminUsername.trim();
  }

  /** BCrypt 输入上限以 UTF-8 字节计算，原口令不 trim。 */
  private static void requirePassword(String password) {
    if (password == null
        || password.isEmpty()
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new ImportException("BOOTSTRAP_CONFIG_INVALID", "员工与管理员初始口令均须配置，且不超过72字节");
    }
  }
}
