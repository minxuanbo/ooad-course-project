package com.municipal.rbaccore.service.port.identity;

import java.util.Optional;
import java.util.Set;

/** 外部身份目录的复杂只读投影，授权与范围决策由两套应用服务分别完成。 */
public interface IdentityDirectoryGateway {
  /** 读取有效部门以核对范围和不存在目标。 */
  Optional<ApplicationDepartmentResult> department(Long id);

  /** 在给定范围内按直接父级、关键词和页窗口读取组织。 */
  ApplicationDirectoryPage<ApplicationDepartmentResult> departments(
      Long rootId, Long parentId, Set<Long> scope, String keyword, int page, int pageSize);

  /** 只查询指定部门直属人员，不隐式展开整个子树。 */
  ApplicationDirectoryPage<ApplicationDepartmentUserResult> users(
      Long departmentId, String keyword, int page, int pageSize);
}
