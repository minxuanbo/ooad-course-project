package com.municipal.rbac;

import java.nio.file.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfrastructureBoundaryTest {
  @TempDir Path dir;
  @Test void schemaGuardRejectsLegacyAndOnlyAddsMissingForeignKey(){
    JdbcTemplate db=mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(1);
    assertThrows(IllegalStateException.class,()->new SchemaGuard(db).run(null));
    verify(db,never()).execute(anyString());
    reset(db);when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0,1);
    new SchemaGuard(db).run(null);verify(db,never()).execute(anyString());
    reset(db);when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0,0);
    new SchemaGuard(db).run(null);verify(db).execute(contains("fk_department_manager"));
  }
  @Test void initializationDisabledOrExistingUsersNeverReadFiles()throws Exception{
    JdbcTemplate db=mock(JdbcTemplate.class);
    DataInitializer init=new DataInitializer(db,dir.toString());
    ReflectionTestUtils.setField(init,"initialize",false);init.run(null);verifyNoInteractions(db);
    ReflectionTestUtils.setField(init,"initialize",true);
    when(db.queryForObject("SELECT count(*) FROM users",Integer.class)).thenReturn(2);
    init.run(null);verify(db).queryForObject("SELECT count(*) FROM users",Integer.class);
    verifyNoMoreInteractions(db);
  }
  @Test void nullAuthorizationFailsBeforeDatabaseAccess(){
    JdbcTemplate db=mock(JdbcTemplate.class);
    AuthService auth=new AuthService(db,12);
    assertThrows(ApiException.class,()->auth.require(null));
    assertThrows(ApiException.class,()->auth.require("Basic abc"));
    auth.logout(null);auth.logout("Basic abc");verifyNoInteractions(db);
  }
  @Test void workbookFallbackSparseColumnsSharedAndNumericCells()throws Exception{
    Path file=dir.resolve("市政公司应用软件功能权限清单(1).xlsx");
    try(var z=new ZipOutputStream(Files.newOutputStream(file))){
      entry(z,"xl/sharedStrings.xml","<sst>\n<si><t>Hello</t></si>\n<si><r><t>A</t></r><r><t>B</t></r></si>\n</sst>");
      entry(z,"xl/worksheets/sheet1.xml","<worksheet>\n<sheetData><row><c r='A1' t='s'><v>0</v></c><c r='C1'><v>42</v></c><c r='AA1' t='s'><v>1</v></c><c r='AB' t='s'><v></v></c></row></sheetData></worksheet>");
    }
    assertEquals(file,ExcelXmlReader.permissionFile(dir));
    var row=ExcelXmlReader.read(file).get(0);
    assertEquals("Hello",row.get(0));assertEquals("",row.get(1));assertEquals("42",row.get(2));assertEquals("AB",row.get(26));assertEquals("",row.get(27));
    Path preferred=dir.resolve("市政公司应用软件功能权限清单.xlsx");Files.copy(file,preferred);
    assertEquals(preferred,ExcelXmlReader.permissionFile(dir));
  }
  @Test void malformedWorkbookFailsRatherThanReturningPartialData()throws Exception{
    Path file=dir.resolve("bad.xlsx");
    Files.writeString(file,"not a zip");assertThrows(Exception.class,()->ExcelXmlReader.read(file));
  }
  void entry(ZipOutputStream z,String name,String text)throws Exception{
    z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();
  }
}
