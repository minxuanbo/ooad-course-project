package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ImportSourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ImportSourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.port.department.DepartmentType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 框架适配层负责组织实体转换及可信根行的物理锁。 */
@Repository
@RequiredArgsConstructor
public class JpaOrganizationRepositoryAdapter implements OrganizationRepository {
  private final DepartmentJpaRepository rows;
  private final UserJpaRepository users;
  private final ImportSourceJpaRepository sources;

  /** 加锁前只取来源指向的编号，确保锁后读取新事实。 */
  @Override
  public Optional<Department.Key> trustedRoot() {
    return sources
        .findSourceDepartmentId(ImportSourceEntity.Kind.ORGANIZATION, "总公司")
        .map(Department.Key::new);
  }

  /** 根证据来自独立导入来源，不使用可改的部门名称。 */
  @Override
  public OrganizationFacts load() {
    Long root = trustedRoot().map(Department.Key::value).orElse(null);
    List<Department> departments =
        rows.findAll().stream()
            .filter(row -> !row.isDeleted())
            .map(JpaDomainMapper::department)
            .toList();
    return new OrganizationFacts(root, departments);
  }

  /** 根据稳定ID返回存储事实。 */
  @Override
  public Optional<Department> find(Department.Key key) {
    return rows.findById(key.value()).map(JpaDomainMapper::department);
  }

  /** 与原策略锁定同一张表的同一行。 */
  @Override
  public Optional<Department> lock(Department.Key key) {
    return JpaWriteErrors.persist(
        "部门", false, () -> rows.lockById(key.value()).map(JpaDomainMapper::department));
  }

  /** 空节点判断保留停用账号及有效子部门事实。 */
  @Override
  public OrganizationFacts.Usage usage(Department.Key key) {
    return new OrganizationFacts.Usage(
        users.existsByDepartmentId(key.value()), rows.existsByParentIdAndDeletedFalse(key.value()));
  }

  /** 仅新建节点需绑定父级，已有节点不更新类型或父级。 */
  @Override
  public Department save(Department value) {
    return JpaWriteErrors.persist(
        "部门",
        false,
        () -> {
          DepartmentEntity row;
          if (value.id() == null) {
            row = new DepartmentEntity(value.code(), value.name());
            row.setType(DepartmentType.valueOf(value.type().name()));
            row.moveTo(rows.getReferenceById(value.parentId()));
          } else {
            row = rows.findById(value.id()).orElseThrow();
            row.rename(value.name());
            row.setDeleted(value.deleted());
          }
          return JpaDomainMapper.department(rows.saveAndFlush(row));
        });
  }
}
