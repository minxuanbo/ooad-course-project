package com.municipal.rbaccore.infrastructure.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T12保留原基础设施测试中与旧业务无关的工作簿读取边界。 */
class R03ExcelReaderBoundaryTest {
  @TempDir Path directory;

  /** 共享字符串富文本、数值与跨Z稀疏列不丢失，原名优先于带序号备选名。 */
  @Test
  @DisplayName("R03 保留共享字符串、稀疏列与权限工作簿优先回退检查")
  void R03_PreservesSharedStringsSparseColumnsAndWorkbookFallback() throws Exception {
    Path fallback = directory.resolve("市政公司应用软件功能权限清单(1).xlsx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fallback))) {
      entry(
          zip,
          "xl/sharedStrings.xml",
          "<sst><si><t>Hello</t></si><si><r><t>A</t></r><r><t>B</t></r></si></sst>");
      entry(
          zip,
          "xl/worksheets/sheet1.xml",
          "<worksheet><sheetData><row><c r='A1' t='s'><v>0</v></c>"
              + "<c r='C1'><v>42</v></c><c r='AA1' t='s'><v>1</v></c>"
              + "<c r='AB' t='s'><v></v></c></row></sheetData></worksheet>");
    }
    assertEquals(fallback, ExcelXmlReader.permissionFile(directory));
    List<String> row = ExcelXmlReader.read(fallback).getFirst();
    assertEquals("Hello", row.get(0));
    assertEquals("", row.get(1));
    assertEquals("42", row.get(2));
    assertEquals("AB", row.get(26));
    assertEquals("", row.get(27));
    Path preferred = directory.resolve("市政公司应用软件功能权限清单.xlsx");
    Files.copy(fallback, preferred);
    assertEquals(preferred, ExcelXmlReader.permissionFile(directory));
  }

  /** 损坏文件必须显式失败，不能当作空数据或返回部分工作簿。 */
  @Test
  @DisplayName("R03 保留坏ZIP工作簿拒绝检查")
  void R03_RejectsMalformedWorkbook() throws IOException {
    Path file = directory.resolve("bad.xlsx");
    Files.writeString(file, "not a zip");
    assertThrows(Exception.class, () -> ExcelXmlReader.read(file));
  }

  /** 生成真实ZIP工作簿条目，不模拟读取器行为。 */
  private void entry(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
