package com.municipal.rbac;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** T03复用读取器，覆盖无共享字符串表的前导零和实际行号。 */
class ExcelImportT03Test {
  @TempDir Path directory;

  /** inlineStr不用sharedStrings，错误dimension仍不得截断实际单元格。 */
  @Test
  @DisplayName("T03 inlineStr、前导零、稀疏列及源行号保持完整")
  void t03ReadsInlineStringsAndRealRowReferences() throws Exception {
    Path file = directory.resolve("inline.xlsx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zip.write(("<worksheet><dimension ref='A1'/><sheetData><row r='7'>"
          + "<c r='A7' t='inlineStr'><is><t>0000123</t></is></c>"
          + "<c r='C7' t='inlineStr'><is><r><t>00</t></r><r><t>0456</t></r></is></c>"
          + "</row></sheetData></worksheet>").getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    List<ExcelXmlReader.WorkbookRow> rows = ExcelXmlReader.readRows(file);
    assertEquals(7, rows.getFirst().number());
    assertEquals(List.of("0000123", "", "000456"), rows.getFirst().cells());
  }
}
