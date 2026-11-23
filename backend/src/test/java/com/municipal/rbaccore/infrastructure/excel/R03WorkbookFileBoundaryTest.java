package com.municipal.rbaccore.infrastructure.excel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.shared.TestProjectPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/** R03文件级边界使用临时真实XLSX，老师原件保持只读。 */
class R03WorkbookFileBoundaryTest {
  @TempDir
  Path directory;

  /** 原始标识摘要不可用时中止稳定编码，不生成占位编码继续导入。 */
  @Test
  @DisplayName("R03 摘要提供者缺失时不生成组织稳定编码")
  void R03_missingDigestProviderRejectsStableCodes() throws Exception {
    java.security.NoSuchAlgorithmException failure = new java.security.NoSuchAlgorithmException("provider unavailable");
    try (MockedStatic<java.security.MessageDigest> digest = mockStatic(java.security.MessageDigest.class)) {
      digest.when(() -> java.security.MessageDigest.getInstance("SHA-256")).thenThrow(failure);
      assertSame(failure, assertThrows(IllegalStateException.class, () -> WorkbookImportPlanner.code("总公司")).getCause());
    }
  }

  /** 缺文件及两种数量不完整分别报告错误，不返回部分可导入计划。 */
  @Test
  @DisplayName("R03 缺文件和组织或员工数量不完整时拒绝读取")
  void R03_rejectsMissingFilesAndIncompleteCounts() throws Exception {
    WorkbookImportPlanner planner = new WorkbookImportPlanner();
    assertEquals("SOURCE_READ_FAILED", assertThrows(ImportException.class, () -> planner.read(directory)).getCode());
    workbook(directory.resolve(WorkbookImportPlanner.ORGANIZATION_FILE), List.of(
        List.of("部门名称", "部门经理", "上级部门"), List.of("总公司", "负责人", "")));
    workbook(directory.resolve(WorkbookImportPlanner.EMPLOYEE_FILE),
        List.of(List.of("姓名", "用户名", "分公司", "部门", "岗位", "电话号码")));
    assertEquals("SOURCE_COUNT_MISMATCH", assertThrows(ImportException.class, () -> planner.read(directory)).getCode());
    Files.copy(TestProjectPaths.sources().resolve(WorkbookImportPlanner.ORGANIZATION_FILE),
        directory.resolve(WorkbookImportPlanner.ORGANIZATION_FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertEquals("SOURCE_COUNT_MISMATCH", assertThrows(ImportException.class, () -> planner.read(directory)).getCode());
  }

  /** 在解析刚结束后确定性模拟并发改文件，二次摘要必须发现组织或员工变化。 */
  @Test
  @DisplayName("R03 读取期间任一原件变化均拒绝，绝不读取后继续提交旧计划")
  void R03_rejectsChangesDuringRead() throws Exception {
    for (String name : List.of(WorkbookImportPlanner.ORGANIZATION_FILE, WorkbookImportPlanner.EMPLOYEE_FILE)) {
      Path run = Files.createTempDirectory(directory, "changed-");
      for (String file : List.of(WorkbookImportPlanner.ORGANIZATION_FILE, WorkbookImportPlanner.EMPLOYEE_FILE)) {
        Files.copy(TestProjectPaths.sources().resolve(file), run.resolve(file));
      }
      Path changed = run.resolve(name);
      try (MockedStatic<ExcelXmlReader> reader = mockStatic(ExcelXmlReader.class, CALLS_REAL_METHODS)) {
        reader.when(() -> ExcelXmlReader.readRows(changed)).thenAnswer(invocation -> {
          Object result = invocation.callRealMethod();
          Files.write(changed, new byte[] {0}, StandardOpenOption.APPEND);
          return result;
        });
        assertEquals("SOURCE_CHANGED", assertThrows(ImportException.class,
            () -> new WorkbookImportPlanner().read(run)).getCode());
      }
    }
  }

  /** 输出读取器支持的最小真实ZIP工作表，不依赖办公软件或假造读取结果。 */
  private void workbook(Path path, List<List<String>> rows) throws Exception {
    StringBuilder xml = new StringBuilder("<worksheet><sheetData>");
    int index = 0;
    for (List<String> row : rows) {
      xml.append("<row r='").append(++index).append("'>");
      for (int column = 0; column < row.size(); column++) {
        xml.append("<c r='").append((char) ('A' + column)).append(index)
            .append("' t='inlineStr'><is><t>").append(row.get(column)).append("</t></is></c>");
      }
      xml.append("</row>");
    }
    xml.append("</sheetData></worksheet>");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }
}
