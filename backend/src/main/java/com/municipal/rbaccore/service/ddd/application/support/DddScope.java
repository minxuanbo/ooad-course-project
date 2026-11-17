package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 负责读取一份组织事实并翻译范围失败，组织判断由领域策略执行。 */
@Component
@RequiredArgsConstructor
public class DddScope {
  private final OrganizationRepository organizations;

  /** 缺少可信来源根时停止范围决策。 */
  public OrganizationFacts facts() {
    OrganizationFacts facts = organizations.load();
    if (facts.trustedRootId() == null) throw BusinessException.scopeDenied();
    return facts;
  }

  /** 先检查所请求部门所属范围，再计算其子树。 */
  public OrganizationFacts.Scope filter(OrganizationFacts facts, Long own, Long requested) {
    OrganizationFacts.Scope allowed = OrganizationPolicy.scope(facts, own);
    if (requested == null) return allowed;
    if (!allowed.departmentIds().contains(requested)) {
      if (OrganizationPolicy.global(facts, own)
          && organizations.find(new Department.Key(requested)).isEmpty()) {
        throw new BusinessException(404, "RESOURCE_NOT_FOUND", "部门不存在");
      }
      throw BusinessException.scopeDenied();
    }
    return OrganizationPolicy.scope(facts, requested);
  }
}
