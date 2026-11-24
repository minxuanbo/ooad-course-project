package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationOnboarding;
import java.util.Optional;

/** 接入聚合仓储只使用领域对象和JDK容器。 */
public interface OnboardingRepository {
  /** 从当前库装配应用目录、权限、操作元数据及本应用角色子集。 */
  Optional<ApplicationOnboarding> find(ApplicationOnboarding.Key key);

  /** 保存领域已验证的配置，保留其他应用的关联与所有人员角色。 */
  void save(ApplicationOnboarding value);
}
