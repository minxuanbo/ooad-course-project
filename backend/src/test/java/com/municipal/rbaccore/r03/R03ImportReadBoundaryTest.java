package com.municipal.rbaccore.r03;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.excel.SourceCellsJson;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.*;
import com.municipal.rbaccore.service.port.security.PasswordHashing;
import com.municipal.rbaccore.service.structured.importing.StructuredImportService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** R03导入服务的端口输入边界；真实SQL约束和回滚由MySQL集成测试验证。 */
class R03ImportReadBoundaryTest {
  private static final Path DIRECTORY = Path.of("unused-import-port");
  private final BootstrapCredentials credentials = new BootstrapCredentials("employee", "administrator", "admin");
  private final ImportPlan.Organization root = new ImportPlan.Organization(2, "总公司", "负责人", "", "总公司", "root");
  private final ImportPlan.Organization child = new ImportPlan.Organization(3, "部门", "负责人", "总公司", "部门", "child");

  /** 组织非空而账号为空也不能被当作空库认领。 */
  @Test
  @DisplayName("R03 空账号但非空组织仍拒绝自动合并")
  void R03_existingOrganizationIsNotEmptyDatabase() {
    Fixture fixture = new Fixture(List.of(root));
    when(fixture.departments.count()).thenReturn(1L);
    assertEquals("TARGET_NOT_EMPTY", assertThrows(ImportException.class,
        () -> fixture.service.importDirectory(DIRECTORY, credentials)).getCode());
    verify(fixture.departments, never()).save(any());
    Fixture existingAccount = new Fixture(List.of(root));
    when(existingAccount.users.count()).thenReturn(1L);
    assertEquals("TARGET_NOT_EMPTY", assertThrows(ImportException.class,
        () -> existingAccount.service.importDirectory(DIRECTORY, credentials)).getCode());
    verify(existingAccount.departments, never()).count();
  }

  /** 端口提供乱序计划仍按依赖保存；不可解析的父链明确停止。 */
  @Test
  @DisplayName("R03 结构化导入按父链重排且拒绝无法解析的组织计划")
  void R03_sortsParentsAndRejectsUnresolvedPlan() {
    Fixture fixture = new Fixture(List.of(child, root));
    fixture.service.importDirectory(DIRECTORY, credentials);
    ArgumentCaptor<DepartmentEntity> saved = ArgumentCaptor.forClass(DepartmentEntity.class);
    verify(fixture.departments, times(2)).save(saved.capture());
    assertEquals(List.of("root", "child"), saved.getAllValues().stream().map(DepartmentEntity::getCode).toList());
    assertSame(saved.getAllValues().getFirst(), saved.getAllValues().get(1).getParent());
    Fixture unresolved = new Fixture(List.of(child));
    assertEquals("ORGANIZATION_INVALID", assertThrows(ImportException.class,
        () -> unresolved.service.importDirectory(DIRECTORY, credentials)).getCode());
    verify(unresolved.sources, never()).saveAllAndFlush(any());
  }

  /** 替身只提供已校验计划和存储回传值，不宣称验证数据库。 */
  private static final class Fixture {
    private final DepartmentJpaRepository departments = mock(DepartmentJpaRepository.class);
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final ImportSourceJpaRepository sources = mock(ImportSourceJpaRepository.class);
    private final StructuredImportService service;

    /** 可控计划用于重排与缺父边界，生产原件解析另外实测。 */
    private Fixture(List<ImportPlan.Organization> organizations) {
      SourceWorkbookReader reader = path -> new ImportPlan(organizations, List.of(), "org", "people");
      when(users.countBinaryLoginColumns()).thenReturn(2L);
      when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(departments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      PasswordHashing hashing = mock(PasswordHashing.class);
      when(hashing.encode(any())).thenReturn("already-encoded");
      service = new StructuredImportService(new SourceCellsJson(new ObjectMapper()), departments, users,
          sources, reader, hashing);
    }
  }
}
