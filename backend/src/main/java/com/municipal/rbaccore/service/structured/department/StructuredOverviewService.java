package com.municipal.rbaccore.service.structured.department;

import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.department.*;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 同一数据库快照聚合范围内人员状态和组织分布。 */
@Service
@RequiredArgsConstructor
public class StructuredOverviewService implements OverviewUseCase {
  private final StructuredAuthenticationService auth;
  private final StructuredDepartmentScopeService scope;
  private final StructuredDepartmentService departments;
  private final UserJpaRepository users;

  /** 启用数来自范围数据库聚合，总数包含启用和停用人员。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OverviewResult overview(String header) {
    CurrentIdentity actor = auth.require(header);
    List<DepartmentResult> tree = departments.list(header);
    DepartmentResult root =
        tree.stream()
            .filter(item -> item.getId().equals(actor.departmentId()))
            .findFirst()
            .orElseThrow();
    OverviewResult result = new OverviewResult();
    result.setDepartmentId(root.getId());
    result.setDepartmentName(root.getName());
    result.setTotalUsers(root.getTotalUsers());
    result.setEnabledUsers(users.countByDepartmentIdInAndEnabledTrue(scope.allowedIds(actor)));
    result.setDisabledUsers(result.getTotalUsers() - result.getEnabledUsers());
    result.setTotalDepartments(tree.size());
    result.setDistribution(
        tree.stream().filter(item -> root.getId().equals(item.getParentId())).toList());
    return result;
  }
}
