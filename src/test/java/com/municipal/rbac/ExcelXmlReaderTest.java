package com.municipal.rbac;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExcelXmlReaderTest {
  @Test void readsNonStandardWorkbookDimensions() throws Exception {
    var employees=ExcelXmlReader.read(Path.of("市政公司员工信息表.xlsx"));
    assertEquals(10001,employees.size()); assertEquals("用户名",employees.get(0).get(1)); assertEquals("SG010000",employees.get(10000).get(1));
    var departments=ExcelXmlReader.read(Path.of("市政公司部门信息表.xlsx")); assertEquals(112,departments.size());
    var permissions=ExcelXmlReader.read(ExcelXmlReader.permissionFile(Path.of("."))); assertEquals(361,permissions.size());
  }
}
