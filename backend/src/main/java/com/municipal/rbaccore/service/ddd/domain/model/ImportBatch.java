package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;

/** 全新导入的完整种子，密码已经在应用层分别编码。 */
public record ImportBatch(
    List<DepartmentSeed> departments,
    List<AccountSeed> accounts,
    List<SourceSeed> sources,
    String admin) {
  /** 以稳定编码引用父节点，批量保存时按依赖顺序解析主键。 */
  public record DepartmentSeed(String code, String name, String parentCode, Department.Type type) {}

  /** 每个账号携带独立密码哈希、来源资料及目标稳定编码。 */
  public record AccountSeed(
      String username,
      String encodedPassword,
      String name,
      String phone,
      String position,
      String departmentCode) {}

  /** 组织和员工原始行与目标的关联。 */
  public record SourceSeed(
      InitializationState.Kind kind,
      String key,
      String hash,
      int row,
      List<String> cells,
      String target) {}

  /** 结果仅返回导入数量和独立管理员编号。 */
  public record Receipt(int departments, int created, int existing, Long adminId) {}
}
