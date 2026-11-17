package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import java.util.Optional;

/** 提供组织事实、可信根锁及共享部门的持久化入口。 */
public interface OrganizationRepository {
  /** 返回稳定来源根编号，锁前不得加载组织实体。 */
  Optional<Department.Key> trustedRoot();

  /** 加载有效树及其独立来源指针。 */
  OrganizationFacts load();

  /** 可单独读取墓碑，以区分未知ID与已删除来源。 */
  Optional<Department> find(Department.Key key);

  /** 取得事务结束前一直持有的根行锁。 */
  Optional<Department> lock(Department.Key key);

  /** 读取有无账号与有效下级，包含停用账号。 */
  OrganizationFacts.Usage usage(Department.Key key);

  /** 新增或保存更新后的聚合，墓碑保留原ID和关联。 */
  Department save(Department department);
}
