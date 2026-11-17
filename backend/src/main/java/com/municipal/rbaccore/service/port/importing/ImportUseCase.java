package com.municipal.rbaccore.service.port.importing;

/** 原始资料及账号的离线新项目导入用例。 */
public interface ImportUseCase {

  /** 整批导入或核对既有来源，不重置后续维护结果。 */
  ImportReport importDirectory(java.nio.file.Path directory, BootstrapCredentials credentials);
}
