package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.security.CurrentIdentity;
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
public class CoreUserService {
  private final CoreAuthService auth;
  private final DepartmentScopeService scope;
  private final UserRepository users;
  private final DepartmentRepository departments;

  /** 导航只投影可管理节点，范围根parentId为null而不修改实体。 */
  public List<CoreResults.DepartmentResult> tree(String header) {
    CurrentIdentity actor = auth.require(header);
    return departments.findAllById(scope.allowedIds(actor)).stream()
        .sorted(Comparator.comparing(Department::getId))
        .map(
            department -> {
              CoreResults.DepartmentResult result =
                  CloneFactory.copy(new CoreResults.DepartmentResult(), department);
              result.setParentId(
                  department.getId().equals(actor.departmentId()) || department.getParent() == null
                      ? null
                      : department.getParent().getId());
              return result;
            })
        .toList();
  }

  /** 同一谓词负责列表与总数，用户不能用查询条件扩张授权范围。 */
  public CoreResults.UserPageResult list(String header, CoreResults.UserSearch query) {
    CurrentIdentity actor = auth.require(header);
    List<Long> departmentIds = scope.filteredIds(actor, query.getDepartmentId());
    String search = query.getQ() == null ? "" : query.getQ().trim().toLowerCase(Locale.ROOT);
    Specification<User> specification =
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
    Page<User> page =
        users.findAll(
            specification,
            PageRequest.of(query.getPage(), query.getSize(), Sort.by("id").ascending()));
    List<CoreResults.UserResult> items =
        page.getContent().stream()
            .map(
                user -> {
                  CoreResults.UserResult result =
                      CloneFactory.copy(new CoreResults.UserResult(), user);
                  result.setEnabled(user.isEnabled());
                  result.setDepartmentId(user.getDepartment().getId());
                  result.setDepartmentName(user.getDepartment().getName());
                  return result;
                })
            .toList();
    return new CoreResults.UserPageResult(
        items, page.getTotalElements(), query.getPage(), query.getSize());
  }
}
