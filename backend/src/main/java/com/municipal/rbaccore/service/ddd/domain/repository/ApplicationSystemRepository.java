package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import java.util.Optional;

/** 登记应用及其生命周期持久化，不承担实际授权决策。 */
public interface ApplicationSystemRepository {
  /** 领域条件分页读取应用，不向应用层泄露JPA技术类型。 */
  DomainPage<ApplicationSystem> search(SearchCriteria.Systems criteria);

  /** 保存同一应用事实，新增时领域编号为空，唯一冲突由适配器翻译。 */
  ApplicationSystem save(ApplicationSystem system);

  /** 返回显式目录中的领域系统事实，不在持久化层决定授权。 */
  java.util.List<ApplicationSystem> catalog();

  /** 根据本系统编码定位唯一目录。 */
  Optional<ApplicationSystem> findRbac();
}
