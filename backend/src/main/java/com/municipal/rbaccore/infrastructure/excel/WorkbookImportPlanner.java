package com.municipal.rbaccore.infrastructure.excel;

import com.municipal.rbaccore.infrastructure.excel.ExcelXmlReader.WorkbookRow;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.importing.SourceWorkbookReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在持久化前校验完整工作簿并计算组织合并与人员归属。 */
@org.springframework.stereotype.Component
public class WorkbookImportPlanner implements SourceWorkbookReader {
  public static final String ORGANIZATION_FILE = "市政公司部门信息表.xlsx";
  public static final String EMPLOYEE_FILE = "市政公司员工信息表.xlsx";
  public static final String ROOT_NAME = "总公司";
  private static final String LEADERSHIP_NAME = "公司领导";

  /** 读取老师提供的两份工作簿；校验失败不得返回可执行的导入计划。 */
  public ImportPlan read(Path directory) {
    try {
      Path organizationFile = directory.resolve(ORGANIZATION_FILE);
      Path employeeFile = directory.resolve(EMPLOYEE_FILE);
      String organizationHash = sha256(Files.readAllBytes(organizationFile));
      String employeeHash = sha256(Files.readAllBytes(employeeFile));
      ImportPlan plan =
          plan(
              ExcelXmlReader.readRows(organizationFile),
              ExcelXmlReader.readRows(employeeFile),
              organizationHash,
              employeeHash);
      if (!organizationHash.equals(sha256(Files.readAllBytes(organizationFile)))
          || !employeeHash.equals(sha256(Files.readAllBytes(employeeFile)))) {
        throw new ImportException("SOURCE_CHANGED", "校验期间源文件发生变化，请重新执行");
      }
      if (plan.organizations().size() != 111 || plan.employees().size() != 10000) {
        throw new ImportException("SOURCE_COUNT_MISMATCH", "源文件应包含111个组织节点及10000名员工");
      }
      return plan;
    } catch (ImportException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ImportException("SOURCE_READ_FAILED", "工作簿无法完整读取，请检查文件及单元格格式");
    }
  }

  /** 分离来源解析与文件访问，用同一规则验证完整原表及边界样例。 */
  public ImportPlan plan(
      List<WorkbookRow> organizationRows,
      List<WorkbookRow> employeeRows,
      String organizationHash,
      String employeeHash) {
    requireHeader(organizationRows, List.of("部门名称", "部门经理", "上级部门"));
    requireHeader(employeeRows, List.of("姓名", "用户名", "分公司", "部门", "岗位", "电话号码"));
    Map<String, WorkbookRow> organizations = new LinkedHashMap<>();
    for (WorkbookRow row : organizationRows.subList(1, organizationRows.size())) {
      String name = required(row, 0, 191);
      if (organizations.putIfAbsent(name, row) != null) {
        throw invalid("ORGANIZATION_INVALID", row, "组织名称重复");
      }
    }
    validateTree(organizations);
    List<ImportPlan.Organization> organizationPlans = new ArrayList<>();
    for (Map.Entry<String, WorkbookRow> entry : organizations.entrySet()) {
      WorkbookRow row = entry.getValue();
      String targetName = target(entry.getKey());
      organizationPlans.add(
          new ImportPlan.Organization(
              row.number(),
              cell(row, 0),
              cell(row, 1),
              cell(row, 2),
              targetName,
              code(targetName)));
    }
    List<ImportPlan.Employee> employees = new ArrayList<>();
    List<LoginIdentifiers.Identity> identities = new ArrayList<>();
    for (WorkbookRow row : employeeRows.subList(1, employeeRows.size())) {
      String username = required(row, 1, 100);
      required(row, 0, 100);
      String company = required(row, 2, 191);
      String department = required(row, 3, 191);
      required(row, 4, 191);
      String phone = cell(row, 5).trim();
      if (phone.length() > 32) {
        throw invalid("SOURCE_FIELD_INVALID", row, "手机号超过32字符");
      }
      String sourceDepartment = resolveDepartment(organizations, row, company, department);
      employees.add(
          new ImportPlan.Employee(
              row.number(),
              cell(row, 0),
              cell(row, 1),
              cell(row, 2),
              cell(row, 3),
              cell(row, 4),
              cell(row, 5),
              target(sourceDepartment)));
      identities.add(new LoginIdentifiers.Identity(username, phone));
    }
    LoginIdentifiers.validate(identities);
    return new ImportPlan(
        List.copyOf(organizationPlans), List.copyOf(employees), organizationHash, employeeHash);
  }

  /** 验证唯一共同根、父引用、连通和无环；仅允许已批准的领导节点合并。 */
  private static void validateTree(Map<String, WorkbookRow> organizations) {
    List<String> roots =
        organizations.entrySet().stream()
            .filter(entry -> cell(entry.getValue(), 2).isBlank())
            .map(Map.Entry::getKey)
            .toList();
    if (!roots.equals(List.of(ROOT_NAME))) {
      throw new ImportException("ORGANIZATION_INVALID", "组织必须且只能以总公司为共同根");
    }
    for (Map.Entry<String, WorkbookRow> entry : organizations.entrySet()) {
      Set<String> visited = new HashSet<>();
      String current = entry.getKey();
      while (!current.isEmpty()) {
        WorkbookRow node = organizations.get(current);
        if (node == null || !visited.add(current)) {
          throw invalid("ORGANIZATION_INVALID", entry.getValue(), "上级缺失或组织形成循环");
        }
        current = cell(node, 2).trim();
      }
      if (entry.getKey().equals(LEADERSHIP_NAME)
          && !cell(entry.getValue(), 2).trim().equals(ROOT_NAME)) {
        throw invalid("ORGANIZATION_INVALID", entry.getValue(), "领导节点合并前必须直属总公司");
      }
      if (cell(entry.getValue(), 2).trim().equals(LEADERSHIP_NAME)) {
        throw invalid("ORGANIZATION_INVALID", entry.getValue(), "领导节点有下级，不能直接合并");
      }
    }
  }

  /** 分公司内优先匹配完整名称，再核对其确实位于该公司子树，禁止按姓名猜归属。 */
  private static String resolveDepartment(
      Map<String, WorkbookRow> organizations, WorkbookRow row, String company, String department) {
    if (!organizations.containsKey(company)) {
      throw invalid("DEPARTMENT_UNRESOLVED", row, "分公司不存在");
    }
    String qualified = company + "-" + department;
    String candidate = organizations.containsKey(qualified) ? qualified : department;
    String current = candidate;
    while (organizations.containsKey(current)) {
      if (current.equals(company)) {
        return candidate;
      }
      current = cell(organizations.get(current), 2).trim();
    }
    throw invalid("DEPARTMENT_UNRESOLVED", row, "部门无法唯一匹配所属公司");
  }

  /** 表头必须逐列匹配，避免错列或误读其他工作表。 */
  private static void requireHeader(List<WorkbookRow> rows, List<String> expected) {
    if (rows.isEmpty() || !rows.getFirst().cells().equals(expected)) {
      throw new ImportException("SOURCE_HEADER_INVALID", "工作簿表头与约定不符");
    }
  }

  /** 必填来源字段按数据库容量验证，原值另存，不把截断当作导入成功。 */
  private static String required(WorkbookRow row, int index, int maximum) {
    String value = cell(row, index).trim();
    if (value.isEmpty() || value.length() > maximum) {
      throw invalid("SOURCE_FIELD_INVALID", row, "第" + (index + 1) + "列为空或超长");
    }
    return value;
  }

  /** 稀疏单元格按空值处理，实际必填性由对应字段规则判断。 */
  private static String cell(WorkbookRow row, int index) {
    return index < row.cells().size() ? row.cells().get(index) : "";
  }

  /** 只合并用户已确认的公司领导节点，不扩展到其他部门。 */
  private static String target(String name) {
    return name.equals(LEADERSHIP_NAME) ? ROOT_NAME : name;
  }

  /** 编码取自组织原名的摘要，与文件行序及数据库自增值无关。 */
  static String code(String name) {
    return "org-" + sha256(name.getBytes(StandardCharsets.UTF_8));
  }

  /** SHA-256 用于只读原件核对及稳定来源标识。 */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("运行环境缺少SHA-256", exception);
    }
  }

  /** 错误定位到来源行，不附带完整个人资料。 */
  private static ImportException invalid(String code, WorkbookRow row, String message) {
    return new ImportException(code, "来源第" + row.number() + "行：" + message);
  }
}
