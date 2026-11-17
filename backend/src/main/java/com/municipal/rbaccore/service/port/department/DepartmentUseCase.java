package com.municipal.rbaccore.service.port.department;

import com.municipal.rbaccore.service.port.user.DepartmentResult;
import java.util.List;

/** 组织树与部门维护的共享契约，两套实现复用同一HTTP边界。 */
public interface DepartmentUseCase {
  /** MVC解析输入前检查实际API、人员级别和合法目标范围，写事务锁后仍复核。 */
  void checkTarget(String header, Long id, boolean write);

  /** 返回授权范围内完整层级和数据库聚合人数。 */
  List<DepartmentResult> list(String header);

  /** 在合法类型父级下新增空节点。 */
  DepartmentResult create(String header, CreateDepartmentCommand command);

  /** 仅改名，不允许调整组织层级。 */
  DepartmentResult rename(String header, Long id, RenameDepartmentCommand command);

  /** 删除空节点，来源墓碑保留，不允许恢复或自动导入复活。 */
  void delete(String header, Long id);
}
