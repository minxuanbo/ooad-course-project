package com.municipal.rbaccore.r03;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.ImportPolicy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R03领域来源核查的最小坏事实输入，失败不能被当作可补齐的全新库。 */
class R03ImportPolicyBoundaryTest {
  private final ImportManifest source = new ImportManifest(List.of(
      new ImportManifest.Organization(2, "总公司", "负责人", "", "总公司", "root"),
      new ImportManifest.Organization(3, "工程部", "负责人", "总公司", "工程部", "dept")),
      List.of(new ImportManifest.Person(2, "样例", "001", "总公司", "工程部", "员工", "002", "工程部")),
      "organization-hash", "people-hash");
  private final Department root = new Department(1L, "root", "总公司", null, Department.Type.HEADQUARTERS, false);
  private final Department child = new Department(2L, "dept", "工程部", 1L, Department.Type.DEPARTMENT, false);

  /** 无来源但已有用户或组织都不能被认领；空库仍可进入首次导入。 */
  @Test
  @DisplayName("R03 空库与无来源旧数据严格区分，标识冲突逐账号核查")
  void R03_rejectsUnclaimedDataAndIdentifierCollisions() {
    assertNull(ImportPolicy.existing(source, new InitializationState(true, List.of(), List.of(), List.of()), "admin"));
    assertCode("TARGET_NOT_EMPTY", () -> ImportPolicy.existing(source,
        new InitializationState(true, List.of(root), List.of(), List.of()), "admin"));
    assertCode("TARGET_NOT_EMPTY", () -> ImportPolicy.existing(source,
        new InitializationState(true, List.of(), List.of(account(1L, "u")), List.of()), "admin"));
    assertDoesNotThrow(() -> ImportPolicy.validateIdentifiers(List.of(
        new ImportPolicy.Identity("001", "001"), new ImportPolicy.Identity("002", null),
        new ImportPolicy.Identity("003", " "))));
    assertCode("IDENTIFIER_CONFLICT", () -> ImportPolicy.validateIdentifiers(List.of(
        new ImportPolicy.Identity("001", "002"), new ImportPolicy.Identity("002", null))));
  }

  /** 稳定来源键、行号、摘要、原始列及目标身份缺一不可。 */
  @Test
  @DisplayName("R03 来源内容和目标绑定逐项漂移均拒绝")
  void R03_rejectsSourceAndTargetDrift() {
    InitializationState state = state();
    assertEquals(0, ImportPolicy.existing(source, state, "admin").created());
    for (InitializationState.Source damaged : List.of(
        new InitializationState.Source(InitializationState.Kind.ORGANIZATION, "wrong-key", "organization-hash", 2, source.organizations().getFirst().cells(), 1L, null),
        new InitializationState.Source(InitializationState.Kind.ORGANIZATION, "总公司", "wrong-hash", 2, source.organizations().getFirst().cells(), 1L, null),
        new InitializationState.Source(InitializationState.Kind.ORGANIZATION, "总公司", "organization-hash", 999, source.organizations().getFirst().cells(), 1L, null),
        new InitializationState.Source(InitializationState.Kind.ORGANIZATION, "总公司", "organization-hash", 2, List.of("changed"), 1L, null),
        new InitializationState.Source(InitializationState.Kind.ORGANIZATION, "总公司", "organization-hash", 2, source.organizations().getFirst().cells(), 2L, null))) {
      List<InitializationState.Source> rows = new ArrayList<>(state.sources());
      rows.set(0, damaged);
      String expected = damaged.key().equals("wrong-key") ? "SOURCE_MAPPING_INCOMPLETE" : "SOURCE_CHANGED";
      assertCode(expected, () -> ImportPolicy.inspect(source,
          new InitializationState(true, state.departments(), state.accounts(), rows)));
    }
    assertCode("TARGET_TREE_CHANGED", () -> ImportPolicy.inspect(source,
        new InitializationState(true, List.of(root), state.accounts(), state.sources())));
    Department moved = new Department(2L, "dept", "工程部", null, Department.Type.DEPARTMENT, false);
    assertCode("TARGET_TREE_CHANGED", () -> ImportPolicy.inspect(source,
        new InitializationState(true, List.of(root, moved), state.accounts(), state.sources())));
    assertCode("SOURCE_CHANGED", () -> ImportPolicy.inspect(source,
        new InitializationState(true, state.departments(), List.of(account(3L, "admin")), state.sources())));
    assertCode("SOURCE_CHANGED", () -> ImportPolicy.inspect(source,
        new InitializationState(true, state.departments(), List.of(account(9L, "001"), account(3L, "admin")), state.sources())));
  }

  /** 管理账号必须与独立来源相同，额外账号和缺少管理员不能被重复导入掩盖。 */
  @Test
  @DisplayName("R03 重复导入拒绝管理员绑定改变及无来源新增用户")
  void R03_rejectsAdministratorMismatchAndExtraUsers() {
    InitializationState state = state();
    List<InitializationState.Source> wrong = new ArrayList<>(state.sources());
    wrong.set(3, new InitializationState.Source(InitializationState.Kind.BOOTSTRAP, "admin", null, null, List.of(), null, 1L));
    assertCode("SOURCE_CHANGED", () -> ImportPolicy.existing(source,
        new InitializationState(true, state.departments(), state.accounts(), wrong), "admin"));
    assertCode("SOURCE_CHANGED", () -> ImportPolicy.existing(source,
        new InitializationState(true, state.departments(), List.of(account(1L, "001")), state.sources()), "admin"));
    List<InitializationState.Account> accounts = new ArrayList<>(state.accounts());
    accounts.add(account(9L, "extra"));
    assertCode("SOURCE_MAPPING_INCOMPLETE", () -> ImportPolicy.existing(source,
        new InitializationState(true, state.departments(), accounts, state.sources()), "admin"));
  }

  /** 层级可以按依赖重排，缺失父级则整体停止而不是丢掉该节点。 */
  @Test
  @DisplayName("R03 组织种子按父级依赖排序，无法建立的父链拒绝")
  void R03_ordersParentsAndRejectsUnresolvableHierarchy() {
    ImportManifest reversed = new ImportManifest(source.organizations().reversed(), source.people(), "org", "people");
    assertEquals(List.of("root", "dept"), ImportPolicy.batch(reversed, List.of(), "admin").departments()
        .stream().map(ImportBatch.DepartmentSeed::code).toList());
    ImportManifest unresolved = new ImportManifest(List.of(
        new ImportManifest.Organization(2, "工程部", "负责人", "不存在", "工程部", "dept")), List.of(), "org", "people");
    assertCode("ORGANIZATION_INVALID", () -> ImportPolicy.batch(unresolved, List.of(), "admin"));
    ImportManifest repeated = new ImportManifest(List.of(source.organizations().getFirst(),
        source.organizations().getFirst(), source.organizations().get(1)), List.of(), "org", "people");
    assertEquals(List.of("root", "dept"), ImportPolicy.batch(repeated, List.of(), "admin").departments()
        .stream().map(ImportBatch.DepartmentSeed::code).toList());
  }

  /** 构造与两行组织、一行员工及独立管理员一一对应的核验事实。 */
  private InitializationState state() {
    List<InitializationState.Source> rows = new ArrayList<>();
    for (int i = 0; i < source.organizations().size(); i++) {
      ImportManifest.Organization organization = source.organizations().get(i);
      rows.add(new InitializationState.Source(InitializationState.Kind.ORGANIZATION, organization.name(),
          source.organizationHash(), organization.row(), organization.cells(), (long) i + 1, null));
    }
    ImportManifest.Person person = source.people().getFirst();
    rows.add(new InitializationState.Source(InitializationState.Kind.EMPLOYEE, "001", source.peopleHash(),
        person.row(), person.cells(), null, 1L));
    rows.add(new InitializationState.Source(InitializationState.Kind.BOOTSTRAP, "admin", null, null, List.of(), null, 3L));
    return new InitializationState(true, List.of(root, child), List.of(account(1L, "001"), account(3L, "admin")), rows);
  }

  /** 不同账号保持不同手机号，避免无关标识冲突遮住目标来源错误。 */
  private InitializationState.Account account(Long id, String username) {
    return new InitializationState.Account(id, username, null, 2L, "dept", 0);
  }

  /** 明确错误原因属于领域核验协议，不只验证出现任意异常。 */
  private void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(InitializationViolation.class, action).code());
  }
}
