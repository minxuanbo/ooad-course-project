package com.municipal.rbaccore.service.ddd.application.security;

import com.municipal.rbaccore.service.ddd.application.support.DddRequestAccess;
import com.municipal.rbaccore.service.ddd.application.support.DddScope;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色、资源和日志的全局根门禁。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddManagementAccess implements ManagementAccessUseCase {
  private final DddRequestAccess requests;
  private final DddScope scope;

  /** 实际API和可信共同根同时成立才允许访问。 */
  @Override
  public void require(String header) {
    CurrentIdentity actor = requests.require(header);
    if (!OrganizationPolicy.global(scope.facts(), actor.departmentId())) {
      throw BusinessException.scopeDenied();
    }
  }
}
