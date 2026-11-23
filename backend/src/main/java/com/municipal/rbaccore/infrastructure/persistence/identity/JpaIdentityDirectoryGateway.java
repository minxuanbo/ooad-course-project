package com.municipal.rbaccore.infrastructure.persistence.identity;

import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.service.port.identity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 共享技术适配器只执行范围内的目录投影，不调用structured或DDD应用实现。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaIdentityDirectoryGateway implements IdentityDirectoryGateway {
  private final EntityManager entities;

  /** 墓碑视为不存在，不暴露来源记录或人员关系。 */
  @Override
  public Optional<ApplicationDepartmentResult> department(Long id) {
    DepartmentEntity row = entities.find(DepartmentEntity.class, id);
    if (row == null || row.isDeleted()) return Optional.empty();
    return Optional.of(
        new ApplicationDepartmentResult(
            row.getId(),
            row.getCode(),
            row.getName(),
            row.getParent() == null ? null : row.getParent().getId(),
            false));
  }

  /** 计数与窗口使用相同谓词，第一页只返回范围根，其后只返回直接子级。 */
  @Override
  public ApplicationDirectoryPage<ApplicationDepartmentResult> departments(
      Long rootId, Long parentId, Set<Long> scope, String keyword, int page, int pageSize) {
    String parent = parentId == null ? "d.id=:parent" : "d.parent.id=:parent";
    String from =
        " FROM DepartmentEntity d WHERE d.deleted=false AND d.id IN :scope AND "
            + parent
            + " AND (lower(d.name) LIKE :keyword ESCAPE '!' OR lower(d.code) LIKE :keyword ESCAPE"
            + " '!')";
    Long selected = parentId == null ? rootId : parentId;
    long total =
        entities
            .createQuery("SELECT COUNT(d)" + from, Long.class)
            .setParameter("scope", scope)
            .setParameter("parent", selected)
            .setParameter("keyword", pattern(keyword))
            .getSingleResult();
    long offset = (long) (page - 1) * pageSize;
    if (offset > Integer.MAX_VALUE || offset >= total) {
      return new ApplicationDirectoryPage<>(List.of(), total, page, pageSize);
    }
    TypedQuery<Object[]> query =
        entities.createQuery(
            "SELECT d.id,d.code,d.name,d.parent.id,(SELECT COUNT(c) FROM DepartmentEntity c WHERE"
                + " c.parent.id=d.id AND c.deleted=false)"
                + from
                + " ORDER BY d.id",
            Object[].class);
    List<ApplicationDepartmentResult> rows =
        query
            .setParameter("scope", scope)
            .setParameter("parent", selected)
            .setParameter("keyword", pattern(keyword))
            .setFirstResult((int) offset)
            .setMaxResults(pageSize)
            .getResultList()
            .stream()
            .map(
                row ->
                    new ApplicationDepartmentResult(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        parentId == null ? null : (Long) row[3],
                        ((Number) row[4]).longValue() > 0))
            .toList();
    return new ApplicationDirectoryPage<>(rows, total, page, pageSize);
  }

  /** 不查询手机号，精确部门关联使下属项目人员不会重复计入。 */
  @Override
  public ApplicationDirectoryPage<ApplicationDepartmentUserResult> users(
      Long departmentId, String keyword, int page, int pageSize) {
    String from =
        " FROM UserEntity u WHERE u.department.id=:department AND (lower(u.username) LIKE :keyword"
            + " ESCAPE '!' OR lower(u.name) LIKE :keyword ESCAPE '!')";
    long total =
        entities
            .createQuery("SELECT COUNT(u)" + from, Long.class)
            .setParameter("department", departmentId)
            .setParameter("keyword", pattern(keyword))
            .getSingleResult();
    long offset = (long) (page - 1) * pageSize;
    if (offset > Integer.MAX_VALUE || offset >= total) {
      return new ApplicationDirectoryPage<>(List.of(), total, page, pageSize);
    }
    List<ApplicationDepartmentUserResult> rows =
        entities
            .createQuery(
                "SELECT u.id,u.username,u.name,u.department.id,u.department.name"
                    + from
                    + " ORDER BY u.id",
                Object[].class)
            .setParameter("department", departmentId)
            .setParameter("keyword", pattern(keyword))
            .setFirstResult((int) offset)
            .setMaxResults(pageSize)
            .getResultList()
            .stream()
            .map(
                row ->
                    new ApplicationDepartmentUserResult(
                        row[0].toString(),
                        (String) row[1],
                        (String) row[2],
                        (Long) row[3],
                        (String) row[4]))
            .toList();
    return new ApplicationDirectoryPage<>(rows, total, page, pageSize);
  }

  /** 百分号与下划线按文本搜索，不能变成由调用者提供的通配范围。 */
  private String pattern(String keyword) {
    String value = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
    return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
