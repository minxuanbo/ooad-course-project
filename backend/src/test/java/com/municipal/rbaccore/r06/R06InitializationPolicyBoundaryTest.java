package com.municipal.rbaccore.r06;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.service.ddd.application.support.DddImportManifest;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.ImportPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.InitialAuthorizationPolicy;
import com.municipal.rbaccore.shared.TestProjectPaths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** R03/R06在不可变领域事实边界验证来源核查，真实持久化及回滚另由MySQL契约覆盖。 */
class R06InitializationPolicyBoundaryTest {
  private static final ImportManifest SOURCE = DddImportManifest.from(
      new WorkbookImportPlanner().read(TestProjectPaths.sources()));
  private static final BootstrapPlan PLAN = InitialAuthorizationPolicy.plan(SOURCE, List.of(),
      Map.of(1, "employee", 2, "project_manager", 3, "department_manager", 4, "company_leader", 5, "system_admin"));
  private static final InitializationState STATE = importedState();

  /** 核对完整快照可发布，并对已有回执保留后续合法变化。 */
  @Test
  @DisplayName("R06 完整来源计划可发布，匹配回执不重置后续变更及墓碑")
  void R06_acceptsCompleteStateAndKeepsReceiptChanges() {
    assertEquals(10001, InitialAuthorizationPolicy.validate(PLAN, STATE, new BootstrapPlan.Catalog(true, null)).size());
    List<Department> departments = new ArrayList<>(STATE.departments());
    departments.add(new Department(99999L, "retired", "旧空节点", null, Department.Type.DEPARTMENT, true));
    InitializationState after = new InitializationState(true, departments, STATE.accounts(), STATE.sources());
    assertTrue(InitialAuthorizationPolicy.validate(PLAN, after,
        new BootstrapPlan.Catalog(false, PLAN.manifestHash())).isEmpty());
  }

  /** 计划数量、回执和目录边界分别拒绝，失败不能回退成初次授予。 */
  @Test
  @DisplayName("R06 首次角色计划拒绝不完整来源、失配回执和无回执旧目录")
  void R06_rejectsIncompleteOrUnclaimedInitialization() {
    ImportManifest shortSource = new ImportManifest(List.of(), SOURCE.people(), "org", "people");
    reject(new BootstrapPlan(shortSource, PLAN.employees(), "hash"), STATE, new BootstrapPlan.Catalog(true, null));
    reject(new BootstrapPlan(SOURCE, List.of(), "hash"), STATE, new BootstrapPlan.Catalog(true, null));
    reject(PLAN, STATE, new BootstrapPlan.Catalog(false, "different"));
    reject(PLAN, STATE, new BootstrapPlan.Catalog(false, null));
    List<InitializationState.Source> missing = new ArrayList<>(STATE.sources());
    missing.removeFirst();
    reject(PLAN, new InitializationState(true, STATE.departments(), STATE.accounts(), missing),
        new BootstrapPlan.Catalog(true, null));
    List<Department> disconnected = new ArrayList<>(STATE.departments());
    disconnected.add(new Department(99999L, "untrusted", "另一个根", null, Department.Type.COMPANY, false));
    reject(PLAN, new InitializationState(true, disconnected, STATE.accounts(), STATE.sources()),
        new BootstrapPlan.Catalog(true, null));
  }

  /** 一人已有角色、账号多余或归属变化均拒绝，不能用计划覆盖现状。 */
  @Test
  @DisplayName("R06 首次授权保护管理员及员工的已变更归属和角色")
  void R06_rejectsAccountDriftAndForgedPlanAssignments() {
    InitializationState.Account admin = InitialAuthorizationPolicy.administrator(STATE);
    Long child = STATE.departments().stream().filter(d -> d.parentId() != null).findFirst().orElseThrow().id();
    for (InitializationState changed : List.of(
        editAccount(admin.username(), a -> new InitializationState.Account(a.id(), a.username(), a.phone(), child, "changed", 0)),
        editAccount(admin.username(), a -> new InitializationState.Account(a.id(), a.username(), a.phone(), a.departmentId(), a.departmentCode(), 1)),
        editAccount(SOURCE.people().getFirst().username(), a -> new InitializationState.Account(a.id(), a.username(), a.phone(), a.departmentId(), a.departmentCode(), 1)),
        editAccount(SOURCE.people().getFirst().username(), a -> new InitializationState.Account(a.id(), a.username(), a.phone(), a.departmentId(), "different", 0)))) {
      reject(PLAN, changed, new BootstrapPlan.Catalog(true, null));
    }
    List<InitializationState.Account> extra = new ArrayList<>(STATE.accounts());
    extra.add(new InitializationState.Account(99999L, "extra", null, admin.departmentId(), admin.departmentCode(), 0));
    reject(PLAN, new InitializationState(true, STATE.departments(), extra, STATE.sources()),
        new BootstrapPlan.Catalog(true, null));
    List<BootstrapPlan.Assignment> forged = new ArrayList<>(PLAN.employees());
    forged.set(0, new BootstrapPlan.Assignment("missing-account", 2, Role.ADMIN_LEVEL));
    reject(new BootstrapPlan(SOURCE, forged, PLAN.manifestHash()), STATE, new BootstrapPlan.Catalog(true, null));
  }

  /** 独立管理员只能来自唯一且一致的来源绑定。 */
  @Test
  @DisplayName("R06 管理员来源缺失、重名及错误绑定全部拒绝")
  void R06_rejectsMissingOrMismatchedAdministrator() {
    List<InitializationState.Source> noAdmin = STATE.sources().stream()
        .filter(s -> s.kind() != InitializationState.Kind.BOOTSTRAP).toList();
    assertEquals("BOOTSTRAP_CONFLICT", assertThrows(InitializationViolation.class,
        () -> InitialAuthorizationPolicy.administrator(new InitializationState(true,
            STATE.departments(), STATE.accounts(), noAdmin))).code());
    List<InitializationState.Source> wrong = new ArrayList<>(noAdmin);
    InitializationState.Account admin = InitialAuthorizationPolicy.administrator(STATE);
    wrong.add(new InitializationState.Source(InitializationState.Kind.BOOTSTRAP, "different", null, null,
        List.of(), null, admin.id()));
    assertThrows(InitializationViolation.class, () -> InitialAuthorizationPolicy.administrator(
        new InitializationState(true, STATE.departments(), STATE.accounts(), wrong)));
  }

  /** 标准摘要算法不可用时必须中止计划，不能发布无摘要的回执。 */
  @Test
  @DisplayName("R06 SHA256提供者故障阻止初始化计划")
  void R06_digestFailureStopsPlanning() throws Exception {
    NoSuchAlgorithmException failure = new NoSuchAlgorithmException("provider unavailable");
    try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
      digest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(failure);
      assertSame(failure, assertThrows(IllegalStateException.class,
          () -> InitialAuthorizationPolicy.plan(SOURCE, List.of(), Map.of())).getCause());
    }
  }

  /** 以原件纯数据重建持久化端口应返回的事实，不使用数据库替身证明事务。 */
  private static InitializationState importedState() {
    ImportBatch batch = ImportPolicy.batch(SOURCE, List.of(), "admin");
    Map<String, Department> departments = new LinkedHashMap<>();
    long departmentId = 0;
    for (ImportBatch.DepartmentSeed seed : batch.departments()) {
      departments.put(seed.code(), new Department(++departmentId, seed.code(), seed.name(),
          seed.parentCode() == null ? null : departments.get(seed.parentCode()).id(), seed.type(), false));
    }
    Map<String, String> codes = ImportPolicy.targetCodes(SOURCE);
    Map<String, InitializationState.Account> accounts = new LinkedHashMap<>();
    long userId = 0;
    for (ImportManifest.Person person : SOURCE.people()) {
      Department department = departments.get(codes.get(person.target()));
      accounts.put(person.username(), new InitializationState.Account(++userId, person.username(),
          person.phone(), department.id(), department.code(), 0));
    }
    Department root = departments.get(codes.get(ImportManifest.ROOT_NAME));
    accounts.put("admin", new InitializationState.Account(++userId, "admin", null, root.id(), root.code(), 0));
    List<InitializationState.Source> sources = new ArrayList<>();
    for (ImportBatch.SourceSeed seed : batch.sources()) {
      boolean organization = seed.kind() == InitializationState.Kind.ORGANIZATION;
      sources.add(new InitializationState.Source(seed.kind(), seed.key(), seed.hash(), seed.row(), seed.cells(),
          organization ? departments.get(seed.target()).id() : null,
          organization ? null : accounts.get(seed.target()).id()));
    }
    sources.add(new InitializationState.Source(InitializationState.Kind.BOOTSTRAP, "admin", null, null,
        List.of(), null, userId));
    return new InitializationState(true, List.copyOf(departments.values()), List.copyOf(accounts.values()), sources);
  }

  /** 只改变指定账号的一项事实，其余来源及账号不变。 */
  private InitializationState editAccount(String username, UnaryOperator<InitializationState.Account> edit) {
    return new InitializationState(true, STATE.departments(), STATE.accounts().stream()
        .map(a -> a.username().equals(username) ? edit.apply(a) : a).toList(), STATE.sources());
  }

  /** 初始化层将下层来源问题翻译为统一冲突，不传播实现异常。 */
  private void reject(BootstrapPlan plan, InitializationState state, BootstrapPlan.Catalog catalog) {
    assertEquals("BOOTSTRAP_CONFLICT", assertThrows(InitializationViolation.class,
        () -> InitialAuthorizationPolicy.validate(plan, state, catalog)).code());
  }
}
