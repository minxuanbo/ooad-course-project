package com.municipal.rbaccore.importing;

import java.nio.file.Path;
import com.municipal.rbac.ExcelXmlReader.WorkbookRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 用真实源文件验证 T03 的全量解析、领导节点合并及字符串标识保留。 */
class WorkbookImportPlannerT03Test {

  /** 声明范围 A1 不得截断读取，合并节点不能丢失任何来源行。 */
  @Test
  @DisplayName("T03 111个来源组织映射110个目标，保留10000名员工及来源哈希")
  void t03PlansAllSourceRowsAndMergesLeadershipIntoRoot() {
    ImportPlan plan = new WorkbookImportPlanner().read(Path.of("."));
    assertEquals(111, plan.organizations().size());
    assertEquals(110, plan.organizations().stream()
        .map(ImportPlan.Organization::targetCode).distinct().count());
    assertEquals(10000, plan.employees().size());
    assertEquals("555ae8cb1a1569dda43cf53c47ee37f4ba4e8964f334ec8f2e1ef73699686c40",
        plan.organizationSha256());
    assertEquals("66657800a5e2e252c9d1dedfee2dbd54b694e3a5912166c2bac79c8703b20516",
        plan.employeeSha256());
    Map<String, ImportPlan.Employee> people = plan.employees().stream()
        .collect(Collectors.toMap(ImportPlan.Employee::username, Function.identity()));
    assertEquals("公司领导", people.get("SG000001").department());
    assertEquals("总公司", people.get("SG000001").targetDepartment());
    assertEquals("道路工程分公司", people.get("SG000191").targetDepartment());
    assertEquals("道路工程分公司-财务部", people.get("SG000211").targetDepartment());
    assertEquals("SG010000", plan.employees().getLast().username());
    assertEquals(10, plan.employees().stream()
        .filter(person -> person.targetDepartment().equals("总公司")).count());
  }

  /** 稳定编码与来源行序无关，人员的前导零和原始六列不丢失。 */
  @Test
  @DisplayName("T03 字符串工号手机号保留前导零，编码不依赖行序")
  void t03PreservesLeadingZerosAndStableCodes() {
    WorkbookImportPlanner planner = new WorkbookImportPlanner();
    ImportPlan first = planner.plan(organizations(), employees("00001", "00002", "财务部"), "org", "people");
    List<WorkbookRow> reordered = new ArrayList<>(organizations());
    java.util.Collections.swap(reordered, 1, 3);
    ImportPlan second = planner.plan(reordered, employees("00001", "00002", "财务部"), "org", "people");
    assertEquals("00001", first.employees().getFirst().username());
    assertEquals("00002", first.employees().getFirst().phone());
    assertEquals("道路工程分公司-财务部", first.employees().getFirst().targetDepartment());
    assertEquals(first.organizations().stream().map(ImportPlan.Organization::targetCode).sorted().toList(),
        second.organizations().stream().map(ImportPlan.Organization::targetCode).sorted().toList());
  }

  /** 不把缺失引用、多个根或循环的部分组织导入数据库。 */
  @Test
  @DisplayName("T03 缺失父节点、多根、循环和重名组织均拒绝")
  void t03RejectsInvalidOrganizationTrees() {
    List<List<WorkbookRow>> cases = List.of(
        List.of(orgHeader(), row(2, "总公司", "", ""), row(3, "财务部", "", "不存在")),
        List.of(orgHeader(), row(2, "总公司", "", ""), row(3, "财务部", "", "")),
        List.of(orgHeader(), row(2, "总公司", "", ""), row(3, "甲", "", "乙"), row(4, "乙", "", "甲")),
        List.of(orgHeader(), row(2, "总公司", "", ""), row(3, "总公司", "", "总公司")));
    for (List<WorkbookRow> invalid : cases) {
      assertEquals("ORGANIZATION_INVALID", assertThrows(ImportException.class,
          () -> new WorkbookImportPlanner().plan(invalid, employees("0001", "0002", "财务部"), "org", "people")).getCode());
    }
  }

  /** 合并只对明确的总公司领导节点有效，不能吞掉其下级或误合并分公司。 */
  @Test
  @DisplayName("T03 未满足约定的领导节点合并前置条件时拒绝")
  void t03RejectsUnapprovedLeadershipMerges() {
    List<WorkbookRow> wrongParent = new ArrayList<>(organizations());
    wrongParent.set(2, row(3, "公司领导", "", "道路工程分公司"));
    List<WorkbookRow> withChild = new ArrayList<>(organizations());
    withChild.add(row(8, "领导下级", "", "公司领导"));
    for (List<WorkbookRow> invalid : List.of(wrongParent, withChild)) {
      assertEquals("ORGANIZATION_INVALID", assertThrows(ImportException.class,
          () -> new WorkbookImportPlanner().plan(invalid, employees("0001", "0002", "财务部"), "org", "people")).getCode());
    }
  }

  /** 无法匹配的部门不得退回公司节点，避免隐藏来源错误。 */
  @Test
  @DisplayName("T03 未知部门、错误表头和缺失字段均拒绝")
  void t03RejectsUnresolvedDepartmentsAndInvalidFields() {
    WorkbookImportPlanner planner = new WorkbookImportPlanner();
    assertEquals("DEPARTMENT_UNRESOLVED", assertThrows(ImportException.class,
        () -> planner.plan(organizations(), employees("0001", "0002", "不存在"), "org", "people")).getCode());
    assertEquals("SOURCE_FIELD_INVALID", assertThrows(ImportException.class,
        () -> planner.plan(organizations(), employees(" ", "0002", "财务部"), "org", "people")).getCode());
    assertEquals("SOURCE_HEADER_INVALID", assertThrows(ImportException.class,
        () -> planner.plan(List.of(row(1, "错误表头")), employees("0001", "0002", "财务部"), "org", "people")).getCode());
  }

  /** 最小组织样例保留同名财务部，用分公司列定位正确节点。 */
  private static List<WorkbookRow> organizations() {
    return List.of(orgHeader(), row(2, "总公司", "负责人", ""), row(3, "公司领导", "负责人", "总公司"),
        row(4, "道路工程分公司", "负责人", "总公司"), row(5, "道路工程分公司-财务部", "负责人", "道路工程分公司"),
        row(6, "财务部", "负责人", "总公司"));
  }

  /** 构造真实列顺序的人员样例，不从姓名推导唯一身份。 */
  private static List<WorkbookRow> employees(String username, String phone, String department) {
    return List.of(row(1, "姓名", "用户名", "分公司", "部门", "岗位", "电话号码"),
        row(2, "样例人员", username, "道路工程分公司", department, "样例岗位", phone));
  }

  /** 组织表头与老师原表逐列一致。 */
  private static WorkbookRow orgHeader() {
    return row(1, "部门名称", "部门经理", "上级部门");
  }

  /** 来源行号独立于列表位置。 */
  private static WorkbookRow row(int number, String... cells) {
    return new WorkbookRow(number, List.of(cells));
  }
}
