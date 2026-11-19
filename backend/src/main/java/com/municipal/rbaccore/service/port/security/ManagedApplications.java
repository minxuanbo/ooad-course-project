package com.municipal.rbaccore.service.port.security;

import java.util.Set;

/** 读取已登记的应用编码；系统启停由具体授权链核实，不使用代码白名单。 */
public interface ManagedApplications {
  /** 返回登记全集，包含停用目录以便维护与完整授权子集检查。 */
  Set<String> codes();
}
