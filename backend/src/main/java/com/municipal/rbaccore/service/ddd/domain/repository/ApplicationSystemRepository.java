package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import java.util.Optional;

/** 正式产品只读取稳定RBAC系统，无系统编辑接口。 */
public interface ApplicationSystemRepository {
  /** 根据本系统编码定位唯一目录。 */
  Optional<ApplicationSystem> findRbac();
}
