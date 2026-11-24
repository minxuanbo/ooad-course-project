package com.municipal.rbaccore.infrastructure.persistence.identity;

import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.identity.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 共享技术适配器通过Spring Data读取最小投影，不调用任一具体应用策略。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaIdentityDirectoryGateway implements IdentityDirectoryGateway {
  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;

  /** 墓碑视为不存在，不暴露原始来源或内部人员关系。 */
  @Override
  public Optional<ApplicationDepartmentResult> department(Long id) {
    return departments
        .findById(id)
        .filter(row -> !row.isDeleted())
        .map(
            row ->
                new ApplicationDepartmentResult(
                    row.getId(),
                    row.getCode(),
                    row.getName(),
                    row.getParent() == null ? null : row.getParent().getId(),
                    false));
  }

  /** 计数与窗口条件完全一致，越末页不将long偏移强制转为int。 */
  @Override
  public ApplicationDirectoryPage<ApplicationDepartmentResult> departments(
      Long rootId, Long parentId, Set<Long> scope, String keyword, int page, int pageSize) {
    boolean rootOnly = parentId == null;
    Long selected = rootOnly ? rootId : parentId;
    String search = pattern(keyword);
    long total = departments.countDirectory(rootOnly, selected, scope, search);
    long offset = (long) (page - 1) * pageSize;
    if (offset > Integer.MAX_VALUE || offset >= total)
      return new ApplicationDirectoryPage<>(List.of(), total, page, pageSize);
    List<ApplicationDepartmentResult> items =
        departments
            .directory(rootOnly, selected, scope, search, PageRequest.of(page - 1, pageSize))
            .stream()
            .map(
                row ->
                    new ApplicationDepartmentResult(
                        row.id(),
                        row.code(),
                        row.name(),
                        rootOnly ? null : row.parentId(),
                        row.children() > 0))
            .toList();
    return new ApplicationDirectoryPage<>(items, total, page, pageSize);
  }

  /** 精确部门关联仅返回直属人员，类型转换在Gateway中完成。 */
  @Override
  public ApplicationDirectoryPage<ApplicationDepartmentUserResult> users(
      Long departmentId, String keyword, int page, int pageSize) {
    String search = pattern(keyword);
    long total = users.countDirectory(departmentId, search);
    long offset = (long) (page - 1) * pageSize;
    if (offset > Integer.MAX_VALUE || offset >= total)
      return new ApplicationDirectoryPage<>(List.of(), total, page, pageSize);
    List<ApplicationDepartmentUserResult> items =
        users.directory(departmentId, search, PageRequest.of(page - 1, pageSize)).stream()
            .map(
                row ->
                    new ApplicationDepartmentUserResult(
                        row.id().toString(),
                        row.username(),
                        row.name(),
                        row.departmentId(),
                        row.departmentName()))
            .toList();
    return new ApplicationDirectoryPage<>(items, total, page, pageSize);
  }

  /** 百分号和下划线作普通文字搜索，不允许调用者改变目录查询范围。 */
  private String pattern(String keyword) {
    String value = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
    return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
