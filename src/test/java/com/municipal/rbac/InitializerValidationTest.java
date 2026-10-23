package com.municipal.rbac;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Targeted unit tests for import validation. Full import is also exercised against MySQL. */
class InitializerValidationTest {
  @Test void departmentSourceRejectsUnknownCompanyAndParent(){
    JdbcTemplate db=mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
    var init=new DataInitializer(db,".");
    try(var files=mockStatic(ExcelXmlReader.class)){
      files.when(()->ExcelXmlReader.read(Path.of(".").resolve("市政公司员工信息表.xlsx")))
        .thenReturn(List.of(List.of("header"),List.of("a","1",""),List.of("b","2","分公司")));
      files.when(()->ExcelXmlReader.read(Path.of(".").resolve("市政公司部门信息表.xlsx")))
        .thenReturn(List.of(List.of("header"),List.of(""),List.of("未知部门")));
      var e=assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(init,"seedDepartments"));
      assertTrue(e.getMessage().contains("部门公司归属未确认"));
      files.when(()->ExcelXmlReader.read(Path.of(".").resolve("市政公司员工信息表.xlsx")))
        .thenReturn(List.of(List.of("header"),List.of("a","1","总公司")));
      files.when(()->ExcelXmlReader.read(Path.of(".").resolve("市政公司部门信息表.xlsx")))
        .thenReturn(List.of(List.of("header"),List.of("部门","负责人","未知上级")));
      e=assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(init,"seedDepartments"));
      assertTrue(e.getMessage().contains("上级部门不存在"));
    }
  }
  @Test void ambiguousManagerAndUnmappedEmployeeAreRejected(){
    JdbcTemplate db=mock(JdbcTemplate.class);var init=new DataInitializer(db,".");
    @SuppressWarnings("unchecked") var managers=(Map<Long,String>)ReflectionTestUtils.getField(init,"managerNames");
    managers.put(1L,"同名经理");
    when(db.queryForList(anyString(),eq(Long.class),any(Object[].class))).thenReturn(List.of(1L,2L));
    assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(init,"resolveManagers"));
    when(db.queryForList(anyString(),eq(Long.class),any(Object[].class))).thenReturn(List.of());
    assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(init,"findDepartment","不存在公司","不存在部门"));
  }
  @Test void sparseCellsAndActionSeparatorsHaveDefinedNormalization(){
    assertEquals("",ReflectionTestUtils.invokeMethod(DataInitializer.class,"get",Arrays.asList((String)null),0));
    assertEquals("",ReflectionTestUtils.invokeMethod(DataInitializer.class,"get",List.of(),1));
    assertEquals(Set.of(),ReflectionTestUtils.invokeMethod(DataInitializer.class,"actions",(Object)null));
    assertEquals(Set.of("查看","编辑"),ReflectionTestUtils.invokeMethod(DataInitializer.class,"actions","查看/ /编辑"));
    JdbcTemplate db=mock(JdbcTemplate.class);var init=new DataInitializer(db,".");
    when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
    ReflectionTestUtils.invokeMethod(init,"insertUser","TEST","test","Test"," ","company",null,"position","hash");
    verify(db).update(anyString(),eq("TEST"),eq("test"),eq("Test"),isNull(),isNull(),eq("position"),eq("hash"));
  }
  @Test void permissionsSkipIncompleteRowsAndHandleNonAdminAllMarker(){
    JdbcTemplate db=mock(JdbcTemplate.class);var init=new DataInitializer(db,".");
    when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
    Path file=Path.of("permissions.xlsx");
    try(var files=mockStatic(ExcelXmlReader.class)){
      files.when(()->ExcelXmlReader.permissionFile(Path.of("."))).thenReturn(file);
      files.when(()->ExcelXmlReader.read(file)).thenReturn(List.of(
        List.of("header"),List.of("header"),List.of("","","orphan"),
        List.of("app","module",""),List.of("app","module","feature","desc","查看","全部","无","","编辑")));
      ReflectionTestUtils.invokeMethod(init,"seedPermissions",Map.of("系统管理员",1L,"公司领导",2L,"部门经理",3L,"项目经理",4L,"普通员工",5L));
      verify(db,times(2)).update(eq("INSERT INTO permissions(feature_id,action_code,code) VALUES(?,?,?)"),eq(1L),anyString(),anyString());
    }
  }
}
