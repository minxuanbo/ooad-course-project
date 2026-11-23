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
  @TempDir
  Path directory;

  /** 缺工作表、无所属行及非法列引用都不允许返回半成品。 */
  @Test
  @DisplayName("R03 缺工作表和非法单元格结构失败，CDATA文本完整保留")
  void R03_rejectsMalformedCellStructures() throws Exception {
    Path empty = directory.resolve("empty.xlsx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(empty))) {
      entry(zip, "metadata", "empty");
    }
    assertThrows(Exception.class, () -> ExcelXmlReader.readRows(empty));
    for (String cell : List.of("<c r='A1'><v>1</v></c>", "<row><c><v>1</v></c></row>",
        "<row><c r='XFE1'><v>1</v></c></row>", "<row><c r='a1'><v>1</v></c></row>")) {
      Path bad = directory.resolve("cell-" + Math.abs(cell.hashCode()) + ".xlsx");
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bad))) {
        entry(zip, "xl/worksheets/sheet1.xml", "<worksheet><sheetData>" + cell + "</sheetData></worksheet>");
      }
      assertThrows(Exception.class, () -> ExcelXmlReader.readRows(bad));
    }
    Path text = directory.resolve("text.xlsx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(text))) {
      entry(zip, "xl/sharedStrings.xml", "<sst>\n<si><t><![CDATA[甲&乙]]></t></si>\n</sst>");
      entry(zip, "xl/worksheets/sheet1.xml", "<worksheet><sheetData><row r='5'><c r='A5' t='s'><v>0</v></c>"
          + "<c r='B5' t='inlineStr'><is><t><![CDATA[00123]]></t></is></c></row></sheetData></worksheet>");
    }
    assertEquals(List.of("甲&乙", "00123"), ExcelXmlReader.readRows(text).getFirst().cells());
  }

  /** StAX提供者可将CDATA单独报告；真实解析器两种事件表示应输出相同内容。 */
  @Test
  @DisplayName("R03 CDATA独立事件在值内保留，值外忽略")
  void R03_supportsProviderReportingSeparateCdata() throws Exception {
    Path text = directory.resolve("provider-cdata.xlsx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(text))) {
      entry(zip, "xl/sharedStrings.xml", "<sst><![CDATA[忽略]]><si><t><![CDATA[共享]]></t></si></sst>");
      entry(zip, "xl/worksheets/sheet1.xml", "<worksheet><sheetData><![CDATA[忽略]]><row>"
          + "<c r='A1' t='s'><v>0</v></c><c r='B1' t='inlineStr'><is><t><![CDATA[原值]]></t></is></c>"
          + "</row></sheetData></worksheet>");
    }
    javax.xml.stream.XMLInputFactory factory = javax.xml.stream.XMLInputFactory.newFactory();
    factory.setProperty("http://java.sun.com/xml/stream/properties/report-cdata-event", true);
    try (org.mockito.MockedStatic<javax.xml.stream.XMLInputFactory> factories =
        org.mockito.Mockito.mockStatic(javax.xml.stream.XMLInputFactory.class)) {
      factories.when(javax.xml.stream.XMLInputFactory::newFactory).thenReturn(factory);
      assertEquals(List.of("共享", "原值"), ExcelXmlReader.readRows(text).getFirst().cells());
    }
  }

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
