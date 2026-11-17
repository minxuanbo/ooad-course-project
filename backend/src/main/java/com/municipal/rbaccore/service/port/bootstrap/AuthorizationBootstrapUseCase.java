package com.municipal.rbaccore.service.port.bootstrap;

/** 五角色首次授权的预览与整批初始化用例。 */
public interface AuthorizationBootstrapUseCase {

  /** 只读生成首次清单。 */
  RoleBootstrapPlan preview(java.nio.file.Path directory);

  /** 读取已核实独立管理员账号名。 */
  String initialAdminUsername();

  /** 一次事务发布首次授权和执行回执。 */
  BootstrapReport initialize(java.nio.file.Path directory);
}
