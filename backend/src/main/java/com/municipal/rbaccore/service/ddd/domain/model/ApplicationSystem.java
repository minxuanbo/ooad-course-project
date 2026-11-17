package com.municipal.rbaccore.service.ddd.domain.model;

/** 授权关联归属的系统与它当前的启用状态。 */
public record ApplicationSystem(Long id, String code, String name, boolean enabled) {
  public static final String CODE_RBAC = "rbac";

  /** 当前产品只维护RBAC目录，历史外系统仍可能存在于待核验关联中。 */
  public boolean isRbac() {
    return CODE_RBAC.equals(code);
  }
}
