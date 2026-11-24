package com.municipal.rbaccore.service.ddd.domain.model;

/** 授权关联归属的系统与它当前的启用状态。 */
public record ApplicationSystem(
    Long id, String code, String name, boolean enabled, Connection connectionStatus) {
  public static final String CODE_RBAC = "rbac";

  /** 接入事实与启停状态分开，待接入目录不产生运行时通行能力。 */
  public enum Connection {
    PENDING,
    CONNECTED
  }

  /** 既有明确接入目录的构造入口，旧调用保持原有已接入语义。 */
  public ApplicationSystem(Long id, String code, String name, boolean enabled) {
    this(id, code, name, enabled, Connection.CONNECTED);
  }

  /** 只有显式完成接入的应用才可用于登录及API鉴权。 */
  public boolean connected() {
    return connectionStatus == Connection.CONNECTED;
  }

  /** 判断是否为RBAC管理受众，不限制数据库登记的其他应用目录。 */
  public boolean isRbac() {
    return CODE_RBAC.equals(code);
  }
}
