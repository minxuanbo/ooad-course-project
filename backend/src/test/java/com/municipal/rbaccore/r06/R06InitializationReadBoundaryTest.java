package com.municipal.rbaccore.r06;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.bootstrap.RoleBootstrapPlan;
import com.municipal.rbaccore.service.structured.bootstrap.RoleBootstrapPlanner;
import com.municipal.rbaccore.service.structured.bootstrap.StructuredInitialAuthorizationService;
import com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard;
import com.municipal.rbaccore.shared.TestProjectPaths;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** R06仓储读边界的单元测试：模拟相互矛盾的快照，真实SQL约束和回滚由MySQL契约验证。 */
class R06InitializationReadBoundaryTest {
  private static final ImportPlan SOURCE = new WorkbookImportPlanner().read(TestProjectPaths.sources());
  private static final Path DIRECTORY = Path.of("unused-source-port");

  /** 任一目录仓储报告已有定义就不能认领，不能依赖其他计数恰好相符。 */
  @Test
  @DisplayName("R06 任一授权目录报告非空均拒绝无回执的首次认领")
  void R06_eachCatalogRejectsUnreceiptedData() throws Exception {
    List<Consumer<Fixture>> cases = List.of(f -> when(f.roles.count()).thenReturn(1L),
        f -> when(f.systems.count()).thenReturn(1L), f -> when(f.permissions.count()).thenReturn(1L),
        f -> when(f.apis.count()).thenReturn(1L));
    for (Consumer<Fixture> change : cases) {
      Fixture fixture = new Fixture();
      change.accept(fixture);
      assertConflict(() -> fixture.service.preview(DIRECTORY));
      verify(fixture.receipts, never()).saveAndFlush(any());
    }
  }

  /** 来源读取不一致必须失败关闭；下层异常不能变成初次授权成功。 */
  @Test
  @DisplayName("R06 来源快照缺损、目标错误、根不完整和管理员绑定错误全部拒绝")
  void R06_rejectsInconsistentSourceReads() throws Exception {
    List<Consumer<Fixture>> cases = List.of(
        f -> when(f.sources.findAll()).thenReturn(f.records.subList(1, f.records.size())),
        f -> when(f.users.countBinaryLoginColumns()).thenReturn(1L),
        f -> ReflectionTestUtils.setField(f.records.getFirst(), "sourceKey", "missing-source-key"),
        f -> ReflectionTestUtils.setField(f.records.getFirst(), "fileSha256", "different"),
        f -> ReflectionTestUtils.setField(f.records.getFirst(), "sourceRow", 999),
        f -> ReflectionTestUtils.setField(f.records.getFirst(), "sourceJson", "[]"),
        f -> ReflectionTestUtils.setField(f.records.getFirst(), "department", f.organization.values().stream().filter(d -> d != f.root).findFirst().orElseThrow()),
        f -> when(f.sources.findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司")).thenReturn(Optional.empty()),
        f -> when(f.departments.subtreeIds(f.root.getId())).thenReturn(List.of(f.root.getId())),
        f -> ReflectionTestUtils.setField(f.root, "parent", new DepartmentEntity("foreign", "另一个根")),
        f -> when(f.sources.findAllByKind(ImportSourceEntity.Kind.BOOTSTRAP)).thenReturn(List.of()),
        f -> ReflectionTestUtils.setField(f.adminSource, "sourceKey", "different-admin"));
    for (Consumer<Fixture> change : cases) {
      Fixture fixture = new Fixture();
      change.accept(fixture);
      assertConflict(() -> fixture.service.preview(DIRECTORY));
      verify(fixture.receipts, never()).saveAndFlush(any());
    }
    Fixture serializer = new Fixture();
    ObjectMapper broken = mock(ObjectMapper.class);
    when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("serialization failure") {});
    assertEquals("来源内容无法核对", assertThrows(BusinessException.class,
        () -> serializer.service(broken).preview(DIRECTORY)).getMessage());
  }

  /** 同一来源和账号查询返回不同集合时，不因总数正确就忽略个体归属。 */
  @Test
  @DisplayName("R06 账号总数、个体缺失、既有角色和部门漂移分别拒绝")
  void R06_checksIndividualAccountsBeyondTotals() throws Exception {
    List<Consumer<Fixture>> cases = List.of(
        f -> when(f.users.findAllForBootstrap()).thenReturn(f.accounts.subList(1, f.accounts.size())),
        f -> { List<UserEntity> changed = new ArrayList<>(f.accounts); changed.set(0, f.extra("unmapped")); when(f.users.findAllForBootstrap()).thenReturn(changed); },
        f -> f.accounts.getFirst().moveTo(f.organization.values().stream().filter(d -> d != f.accounts.getFirst().getDepartment()).findFirst().orElseThrow()),
        f -> f.accounts.getFirst().assignRole(new RoleEntity("prior-role", "原角色")));
    for (Consumer<Fixture> change : cases) {
      Fixture fixture = new Fixture();
      change.accept(fixture);
      assertConflict(() -> fixture.service.preview(DIRECTORY));
    }
  }

  /** 最后一步再次核验管理员，只有完全相同的来源身份和根归属才可写完成标记。 */
  @Test
  @DisplayName("R06 发布前管理员账号缺失、身份改变或非根归属均不写完成回执")
  void R06_rechecksAdministratorBeforeCompletionReceipt() throws Exception {
    for (int scenario = 0; scenario < 3; scenario++) {
      Fixture fixture = new Fixture();
      List<UserEntity> changed = new ArrayList<>(fixture.accounts);
      UserEntity alternative = fixture.extra(scenario == 0 ? "different" : "admin");
      if (scenario == 2) {
        ReflectionTestUtils.setField(alternative, "id", fixture.administrator.getId());
        alternative.moveTo(fixture.organization.values().stream().filter(d -> d != fixture.root).findFirst().orElseThrow());
      }
      changed.set(changed.size() - 1, alternative);
      when(fixture.users.findAllForBootstrap()).thenReturn(changed);
      assertConflict(() -> fixture.service.initialize(DIRECTORY));
      verify(fixture.receipts, never()).saveAndFlush(any());
      verify(fixture.guard, never()).retainOne();
    }
  }

  /** 原件或规则摘要不符时已有回执不能被当成幂等成功。 */
  @Test
  @DisplayName("R06 已有完成回执摘要不一致时拒绝再次发布")
  void R06_receiptHashMismatchRejectsPublish() throws Exception {
    Fixture fixture = new Fixture();
    when(fixture.receipts.findById("rbac-initial-roles")).thenReturn(Optional.of(
        new BootstrapReceiptEntity("different", Instant.parse("2026-11-23T00:00:00Z"))));
    assertConflict(() -> fixture.service.initialize(DIRECTORY));
    verify(fixture.receipts, never()).saveAndFlush(any());
  }

  /** 来源数相同也不能用另一条记录顶替缺失键，已有回执的预览只核对来源。 */
  @Test
  @DisplayName("R06 来源键缺失明确拒绝，已有回执预览不重复认领目录")
  void R06_missingKeyAndReceiptedPreview() throws Exception {
    Fixture missing = new Fixture();
    List<ImportSourceEntity> changed = new ArrayList<>(missing.records);
    changed.set(0, ImportSourceEntity.bootstrap(missing.extra("unmapped-source")));
    when(missing.sources.findAll()).thenReturn(changed);
    assertConflict(() -> missing.service.preview(DIRECTORY));
    Fixture receipted = new Fixture();
    when(receipted.receipts.existsById("rbac-initial-roles")).thenReturn(true);
    assertEquals(10000, receipted.service.preview(DIRECTORY).assignments().size());
    verify(receipted.roles, never()).count();
  }

  /** 仅检查稳定离线冲突码；数据库事务正确性不由本单元替身宣称。 */
  private void assertConflict(org.junit.jupiter.api.function.Executable action) {
    BusinessException failure = assertThrows(BusinessException.class, action);
    assertEquals(409, failure.getStatus());
    assertEquals("BOOTSTRAP_CONFLICT", failure.getCode());
  }

  /** 原件值与实体快照一一对应，替身只用于控制仓储读边界的不一致结果。 */
  private static final class Fixture {
    private final RoleBootstrapPlanner planner = new RoleBootstrapPlanner(path -> SOURCE);
    private final BootstrapReceiptJpaRepository receipts = mock(BootstrapReceiptJpaRepository.class);
    private final ImportSourceJpaRepository sources = mock(ImportSourceJpaRepository.class);
    private final DepartmentJpaRepository departments = mock(DepartmentJpaRepository.class);
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final RoleJpaRepository roles = mock(RoleJpaRepository.class);
    private final ApplicationSystemJpaRepository systems = mock(ApplicationSystemJpaRepository.class);
    private final PermissionJpaRepository permissions = mock(PermissionJpaRepository.class);
    private final ApiResourceJpaRepository apis = mock(ApiResourceJpaRepository.class);
    private final StructuredMaintenanceGuard guard = mock(StructuredMaintenanceGuard.class);
    private final Map<String, DepartmentEntity> organization = new LinkedHashMap<>();
    private final List<UserEntity> accounts = new ArrayList<>();
    private final List<ImportSourceEntity> records = new ArrayList<>();
    private final DepartmentEntity root;
    private final UserEntity administrator;
    private final ImportSourceEntity adminSource;
    private final StructuredInitialAuthorizationService service;

    /** 用真实原件建立可比对数据，避免靠宽松的mock默认值掩盖前置失败。 */
    private Fixture() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      long id = 0;
      for (ImportPlan.Organization row : SOURCE.organizations()) {
        if (!organization.containsKey(row.targetName())) {
          DepartmentEntity department = new DepartmentEntity(row.targetCode(), row.targetName());
          ReflectionTestUtils.setField(department, "id", ++id);
          organization.put(row.targetName(), department);
        }
      }
      for (ImportPlan.Organization row : SOURCE.organizations()) {
        if (row.name().trim().equals(row.targetName()) && !row.parent().isBlank())
          organization.get(row.targetName()).moveTo(organization.get(row.parent()));
        records.add(ImportSourceEntity.organization(row, SOURCE.organizationSha256(),
            mapper.writeValueAsString(List.of(row.name(), row.manager(), row.parent())), organization.get(row.targetName())));
      }
      root = organization.get("总公司");
      id = 0;
      for (ImportPlan.Employee row : SOURCE.employees()) {
        UserEntity user = new UserEntity(row.username(), "already-encoded", row.name(), organization.get(row.targetDepartment()), true);
        ReflectionTestUtils.setField(user, "id", ++id);
        accounts.add(user);
        records.add(ImportSourceEntity.employee(row, SOURCE.employeeSha256(), mapper.writeValueAsString(
            List.of(row.name(), row.username(), row.company(), row.department(), row.position(), row.phone())), user));
      }
      administrator = new UserEntity("admin", "already-encoded", "维护账号", root, true);
      ReflectionTestUtils.setField(administrator, "id", ++id);
      accounts.add(administrator);
      adminSource = ImportSourceEntity.bootstrap(administrator);
      records.add(adminSource);
      when(sources.findAll()).thenReturn(records);
      when(sources.findAllByKind(ImportSourceEntity.Kind.BOOTSTRAP)).thenReturn(List.of(adminSource));
      when(sources.findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司"))
          .thenReturn(records.stream().filter(s -> s.getSourceKey().equals("总公司")).findFirst());
      when(users.countBinaryLoginColumns()).thenReturn(2L);
      when(users.findAllForBootstrap()).thenReturn(accounts);
      when(departments.subtreeIds(root.getId())).thenReturn(organization.values().stream().map(DepartmentEntity::getId).toList());
      when(departments.countByDeletedFalse()).thenReturn((long) organization.size());
      when(roles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(systems.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(permissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(apis.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      service = service(mapper);
    }

    /** 初始化服务的序列化器是显式依赖，可独立模拟技术故障而不篡改静态字段。 */
    private StructuredInitialAuthorizationService service(ObjectMapper mapper) {
      return new StructuredInitialAuthorizationService(planner, receipts, sources, departments, users,
          roles, systems, permissions, apis, guard, Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC), mapper);
    }

    /** 不存在于来源的额外读取结果，用于验证计数相同但个体不同也会拒绝。 */
    private UserEntity extra(String username) {
      UserEntity user = new UserEntity(username, "already-encoded", "额外读取", root, true);
      ReflectionTestUtils.setField(user, "id", 999999L);
      return user;
    }
  }
}
