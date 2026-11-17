package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import java.util.Optional;
import java.util.Set;

/** 读取并保存API资源，不把权限是否有效提前算成查询条件。 */
public interface ApiResourceRepository {
  /** 根据身份读取API。 */
  Optional<ApiResource> find(ApiResource.Key key);

  /** 批量读取已存在的API，保留启停和系统归属事实。 */
  Set<ApiResource> select(Set<ApiResource.Key> keys);

  /** 在指定系统使用同筛选表达式读取总数和当前页。 */
  DomainPage<ApiResource> search(SearchCriteria.Apis criteria);

  /** 保存受控的启用状态。 */
  ApiResource save(ApiResource api);
}
