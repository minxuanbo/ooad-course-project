package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApiResourceJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.ApiResourceRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** 按系统查询API目录并写入唯一可变的启用状态。 */
@Repository
@RequiredArgsConstructor
public class JpaApiResourceRepositoryAdapter implements ApiResourceRepository {
  private final ApiResourceJpaRepository rows;

  /** 保留真实所属系统供目标权限检查。 */
  @Override
  public Optional<ApiResource> find(ApiResource.Key key) {
    return rows.findById(key.value()).map(JpaDomainMapper::api);
  }

  /** 读取显式关联要求的API集合。 */
  @Override
  public Set<ApiResource> select(Set<ApiResource.Key> keys) {
    return rows.findAllById(keys.stream().map(ApiResource.Key::value).toList()).stream()
        .map(JpaDomainMapper::api)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 查询窗口只在单一系统内生效。 */
  @Override
  public DomainPage<ApiResource> search(SearchCriteria.Apis criteria) {
    String pattern = JpaDomainQueries.pattern(criteria.q());
    Specification<ApiResourceEntity> specification =
        (root, query, builder) ->
            builder.and(
                builder.equal(root.get("system").get("id"), criteria.system().id()),
                builder.or(
                    builder.like(builder.lower(root.get("httpMethod")), pattern, '\\'),
                    builder.like(builder.lower(root.get("pathTemplate")), pattern, '\\')));
    return JpaDomainQueries.read(
        rows,
        specification,
        criteria.page(),
        criteria.size(),
        page -> page.stream().map(JpaDomainMapper::api).toList());
  }

  /** API启停不会破坏已经保存的权限关联。 */
  @Override
  public ApiResource save(ApiResource api) {
    return JpaWriteErrors.persist(
        "资源",
        true,
        () -> {
          ApiResourceEntity row = rows.findById(api.id()).orElseThrow();
          row.setEnabled(api.enabled());
          return JpaDomainMapper.api(rows.saveAndFlush(row));
        });
  }
}
