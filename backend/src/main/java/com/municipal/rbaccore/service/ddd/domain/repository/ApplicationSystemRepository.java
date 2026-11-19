package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import java.util.Optional;

/** 查询稳定RBAC锚点和显式接入目录，不提供系统编辑接口。 */
public interface ApplicationSystemRepository {
  /** 返回显式目录中的领域系统事实，不在持久化层决定授权。 */
  java.util.List<ApplicationSystem> catalog(java.util.Set<String> codes);

  /** 根据本系统编码定位唯一目录。 */
  Optional<ApplicationSystem> findRbac();
}
