package com.municipal.rbaccore.service.ddd.application.department;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.service.ddd.application.support.DddDepartmentView;
import com.municipal.rbaccore.service.ddd.application.support.DddScope;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.department.OverviewResult;
import com.municipal.rbaccore.service.port.department.OverviewUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 按当前可见部门子树获取人口与组织分布，不混入独立的日志权限。 */
@Service
@RequiredArgsConstructor
public class DddOverviewService implements OverviewUseCase {
  private final AuthenticationUseCase authentication;
  private final UserRepository users;
  private final DddScope scope;
  private final DddDepartmentView departments;

  /** 人数和直接下级分布来自一份组织及人数快照。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OverviewResult overview(String header) {
    User actor =
        users
            .find(new User.Key(authentication.require(header).id()))
            .orElseThrow(BusinessException::unauthorized);
    DddDepartmentView.View view = departments.build(actor, scope.facts());
    DepartmentResult root = view.tree().getFirst();
    OverviewResult result = CloneFactory.copy(new OverviewResult(), root);
    result.setDepartmentId(root.getId());
    result.setDepartmentName(root.getName());
    result.setEnabledUsers(view.enabled());
    result.setDisabledUsers(root.getTotalUsers() - view.enabled());
    result.setTotalDepartments(view.tree().size());
    result.setDistribution(
        view.tree().stream().filter(node -> root.getId().equals(node.getParentId())).toList());
    return result;
  }
}
