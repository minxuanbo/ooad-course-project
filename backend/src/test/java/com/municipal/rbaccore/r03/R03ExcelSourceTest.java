package com.municipal.rbaccore.r03;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.excel.ExcelXmlReader;
import com.municipal.rbaccore.shared.TestProjectPaths;
import org.junit.jupiter.api.Test;

/** R03核对老师原始Excel的实际数据范围，避免错误A1声明截断读取。 */
class R03ExcelSourceTest {
  /** 读取原表实际单元格，验证三份Excel不被错误声明范围截断。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R03/R04/R05/R06 读取原表实际单元格，验证三份Excel不被错误声明范围截断。")
  void R03_readsNonStandardWorkbookDimensions() throws Exception {
    java.util.List<java.util.List<String>> employees =
        ExcelXmlReader.read(TestProjectPaths.sources().resolve("市政公司员工信息表.xlsx"));
    assertEquals(10001, employees.size());
    assertEquals("用户名", employees.get(0).get(1));
    assertEquals("SG010000", employees.get(10000).get(1));
    java.util.List<java.util.List<String>> departments =
        ExcelXmlReader.read(TestProjectPaths.sources().resolve("市政公司部门信息表.xlsx"));
    assertEquals(112, departments.size());
    java.util.List<java.util.List<String>> permissions =
        ExcelXmlReader.read(ExcelXmlReader.permissionFile(TestProjectPaths.sources()));
    assertEquals(361, permissions.size());
  }
}
