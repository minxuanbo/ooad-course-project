package com.municipal.rbaccore.service.structured.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import com.municipal.rbaccore.service.port.user.UserPageResult;
import com.municipal.rbaccore.service.port.user.UserQuery;
import com.municipal.rbaccore.service.port.user.UserQueryUseCase;
import com.municipal.rbaccore.service.port.user.UserResult;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 数据库先限制部门范围，再应用搜索、筛选、计数与分页。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredUserQueryService implements UserQueryUseCase {
  private final StructuredAuthenticationService auth;
  private final StructuredDepartmentScopeService scope;
  private final UserJpaRepository users;
  private final DepartmentJpaRepository departments;

  /** 导航只投影可管理节点，范围根parentId为null而不修改实体。 */
  public List<DepartmentResult> tree(String header) {
    CurrentIdentity actor = auth.require(header);
    return departments.findAllById(scope.allowedIds(actor)).stream()
        .sorted(Comparator.comparing(DepartmentEntity::getId))
        .map(
            department -> {
              DepartmentResult result = CloneFactory.copy(new DepartmentResult(), department);
              result.setParentId(
                  department.getId().equals(actor.departmentId()) || department.getParent() == null
                      ? null
                      : department.getParent().getId());
              return result;
            })
        .toList();
  }

  /** 同一谓词负责列表与总数，用户不能用查询条件扩张授权范围。 */
  public UserPageResult list(String header, UserQuery query) {
    CurrentIdentity actor = auth.require(header);
    List<Long> departmentIds = scope.filteredIds(actor, query.getDepartmentId());
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    Specification<UserEntity> specification =
        (root, criteria, builder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(root.get("department").get("id").in(departmentIds));
          if (!search.isEmpty()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            predicates.add(
                builder.or(
                    builder.like(builder.lower(root.get("name")), "%" + escaped + "%", '\\'),
                    builder.like(builder.lower(root.get("username")), "%" + escaped + "%", '\\')));
          }
          if (query.getEnabled() != null) {
            predicates.add(builder.equal(root.get("enabled"), query.getEnabled()));
          }
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    // 先用同一受限谓词判断越末页，避免JPA将极大合法页码转换为int偏移时失败。
    long total = users.count(specification);
    long offset = (long) query.getPage() * query.getSize();
    if (offset >= total) {
      return new UserPageResult(List.of(), total, query.getPage(), query.getSize());
    }
    Page<UserEntity> page =
        users.findAll(
            specification,
            PageRequest.of(query.getPage(), query.getSize(), Sort.by("id").ascending()));
    List<UserResult> items =
        page.getContent().stream()
            .map(
                user -> {
                  UserResult result = CloneFactory.copy(new UserResult(), user);
                  result.setEnabled(user.isEnabled());
                  result.setDepartmentId(user.getDepartment().getId());
                  result.setDepartmentName(user.getDepartment().getName());
                  return result;
                })
            .toList();
    return new UserPageResult(items, total, query.getPage(), query.getSize());
  }
}
