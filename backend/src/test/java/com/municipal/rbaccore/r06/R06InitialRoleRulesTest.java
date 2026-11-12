package com.municipal.rbaccore.r06;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.service.port.bootstrap.InitialRole;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.structured.bootstrap.InitialRoleRules;
import com.municipal.rbaccore.shared.TestProjectPaths;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T05首次授权使用用户确认的五角色规则，而不是旧初始化器的隐含岗位推断。 */
class R06InitialRoleRulesTest {

  /** 对真实一万名员工生成完整且互斥的首次角色清单。 */
  @Test
  @DisplayName("R06 五角色清单覆盖10000员工，公司领导64、部门经理106、项目经理72、普通9758")
  void R06_ClassifiesAllOriginalEmployees() {
    ImportPlan plan = new WorkbookImportPlanner().read(TestProjectPaths.sources());
    Map<InitialRole, Long> counts =
        plan.employees().stream()
            .collect(Collectors.groupingBy(InitialRoleRules::classify, Collectors.counting()));
    assertEquals(
        Map.of(
            InitialRole.COMPANY_LEADER,
            64L,
            InitialRole.DEPARTMENT_MANAGER,
            106L,
            InitialRole.PROJECT_MANAGER,
            72L,
            InitialRole.EMPLOYEE,
            9758L),
        counts);
    assertFalse(counts.containsKey(InitialRole.SYSTEM_ADMIN));
  }

  /** 主任与项目总工不在本次提升规则中，不能把相似岗位自动授为管理角色。 */
  @Test
  @DisplayName("R06 精确匹配部长副部长、项目经理副经理，其余岗位保持普通员工")
  void R06_DoesNotPromoteUnapprovedPositions() {
    assertEquals(InitialRole.DEPARTMENT_MANAGER, InitialRoleRules.classify(employee("财务部", "副部长")));
    assertEquals(
        InitialRole.PROJECT_MANAGER, InitialRoleRules.classify(employee("第一项目经理部", "项目副经理")));
    assertEquals(InitialRole.EMPLOYEE, InitialRoleRules.classify(employee("综合办公室", "主任")));
    assertEquals(InitialRole.EMPLOYEE, InitialRoleRules.classify(employee("第一项目经理部", "项目总工程师")));
  }

  /** 构造人员来源，保留原部门与目标部门两种含义。 */
  private static ImportPlan.Employee employee(String department, String position) {
    return new ImportPlan.Employee(
        2, "样例", "00001", "道路工程分公司", department, position, "00000000001", "道路工程分公司-" + department);
  }
}
