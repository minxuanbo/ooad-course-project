package com.municipal.rbac;

import java.util.Set;

/** Explicit mapping of the source job titles to the five existing permission-matrix roles. */
final class RolePolicy {
  private RolePolicy(){}
  static String primary(String position,String department,boolean namedManager){
    if(Set.of("项目经理","项目副经理","项目总工程师").contains(position))return "项目经理";
    if(Set.of("公司领导","分公司领导").contains(department) || Set.of("董事长","总经理","副总经理","总工程师","总会计师","党委副书记","工会主席","总经理助理","董事会秘书","经理","副经理","经理助理","安全总监").contains(position))return "公司领导";
    if(Set.of("部长","副部长","主任","副主任").contains(position)||namedManager)return "部门经理";
    return "普通员工";
  }
}
