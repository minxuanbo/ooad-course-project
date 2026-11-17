package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApiResourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.PermissionRepository;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 持久化权限对象及映射，业务校验已经在领域执行。 */
@Repository
@RequiredArgsConstructor
public class JpaPermissionRepositoryAdapter implements PermissionRepository {
  private final PermissionJpaRepository rows;
  private final ApiResourceJpaRepository apis;

  /** 按ID读取完整限定关系及启用状态。 */
  @Override
  public Optional<Permission> find(Permission.Key key) {
    return rows.findById(key.value()).map(JpaDomainMapper::permission);
  }

  /** 权限树读取包括当前系统的停用节点。 */
  @Override
  public List<Permission> tree(ApplicationSystem system) {
    return rows.findBySystemIdOrderByIdAsc(system.id()).stream()
        .map(JpaDomainMapper::permission)
        .toList();
  }

  /** 选择项保留系统事实供上层判断。 */
  @Override
  public Set<Permission> select(Permission.Selection selection) {
    return rows.findAllById(selection.ids()).stream()
        .map(JpaDomainMapper::permission)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 同步直接树关系与API变化，保留未变化的关联记录。 */
  @Override
  public Permission save(Permission value) {
    return JpaWriteErrors.persist(
        "资源",
        true,
        () -> {
          PermissionEntity row = rows.findById(value.id()).orElseThrow();
          Long parent = row.getParent() == null ? null : row.getParent().getId();
          if (!row.getType().name().equals(value.type().name())) {
            row.changeType(PermissionType.valueOf(value.type().name()));
          }
          if (!Objects.equals(parent, value.parentId())) {
            row.moveTo(value.parentId() == null ? null : rows.getReferenceById(value.parentId()));
          }
          row.setEnabled(value.enabled());
          Set<Long> ids = value.apis().stream().map(ApiResource::id).collect(Collectors.toSet());
          Set<Long> existing =
              row.getApis().stream().map(item -> item.getId()).collect(Collectors.toSet());
          List.copyOf(row.getApis()).stream()
              .filter(item -> !ids.contains(item.getId()))
              .forEach(row::unmapApi);
          ids.removeAll(existing);
          apis.findAllById(ids).forEach(row::mapApi);
          return JpaDomainMapper.permission(rows.saveAndFlush(row));
        });
  }
}
