package com.municipal.rbaccore.r01;

import static com.municipal.rbaccore.service.port.resource.PermissionType.*;
import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R01/R02/R09/R14验证持久化数据的局部约束，不以此冒充DDD实现。 */
class R01ModelRulesTest {
  private final ApplicationSystemEntity system = new ApplicationSystemEntity("sample", "样例系统");

  /** 构造本测试系统中的权限夹具，不生成额外授权。 */
  private PermissionEntity permission(String code, PermissionType type) {
    /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
    return new PermissionEntity(system, code, code, type);
  }

  /** 部门自指和循环在修改父级前拒绝，原树保持完整。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 部门自指和循环在修改父级前拒绝，原树保持完整。")
  void departmentTreesRejectSelfAndCyclesBeforeChangingParent() {
    DepartmentEntity root = new DepartmentEntity("root", "根");
    DepartmentEntity child = new DepartmentEntity("child", "子");
    DepartmentEntity leaf = new DepartmentEntity("leaf", "叶");
    child.moveTo(root);
    leaf.moveTo(child);
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(root));
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(leaf));
    assertNull(root.getParent());
    assertSame(root, child.getParent());
    child.moveTo(null);
    root.moveTo(leaf);
    assertSame(leaf, root.getParent());
  }

  /** 权限自指、循环和跨系统父级拒绝后，原关联不得丢失。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 权限自指、循环和跨系统父级拒绝后，原关联不得丢失。")
  void permissionTreesRejectSelfCyclesAndCrossSystemParentsWithoutUnlinking() {
    PermissionEntity root = permission("root", MENU);
    PermissionEntity child = permission("child", MENU);
    PermissionEntity leaf = permission("leaf", MENU);
    child.moveTo(root);
    leaf.moveTo(child);
    assertThrows(IllegalArgumentException.class, () -> child.moveTo(child));
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(leaf));
    PermissionEntity other =
        /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
        new PermissionEntity(new ApplicationSystemEntity("other", "其他"), "other", "其他", MENU);
    assertThrows(IllegalArgumentException.class, () -> child.moveTo(other));
    assertSame(root, child.getParent());
    assertEquals(List.of(child), root.getChildren().stream().toList());
    assertNull(other.getParent());
    assertTrue(other.getChildren().isEmpty());
  }

  /** 按钮不能挂子节点，移动子节点后类型检查使用最新集合。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 按钮不能挂子节点，移动子节点后类型检查使用最新集合。")
  void buttonCannotBeParentAndMovingChildrenKeepsTypeChecksCurrent() {
    PermissionEntity oldParent = permission("old", MENU);
    PermissionEntity newParent = permission("new", MENU);
    PermissionEntity button = permission("button", BUTTON);
    button.moveTo(oldParent);
    assertThrows(IllegalArgumentException.class, () -> oldParent.changeType(BUTTON));
    assertThrows(IllegalArgumentException.class, () -> newParent.moveTo(button));
    assertNull(newParent.getParent());
    button.moveTo(newParent);
    oldParent.changeType(BUTTON);
    assertEquals(BUTTON, oldParent.getType());
    assertTrue(oldParent.getChildren().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> newParent.changeType(BUTTON));
    button.moveTo(null);
    newParent.changeType(BUTTON);
    newParent.changeType(MENU);
    oldParent.moveTo(newParent);
    assertSame(newParent, oldParent.getParent());
  }

  /** API映射先检查同系统再变更，拒绝时不留部分关联。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 API映射先检查同系统再变更，拒绝时不留部分关联。")
  void apiMappingsRequireSameSystemAndRejectBeforeMutating() {
    PermissionEntity permission = permission("read", BUTTON);
    ApiResourceEntity api = new ApiResourceEntity(system, " get ", "/__t02__/sample");
    ApiResourceEntity other =
        /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
        new ApiResourceEntity(
            /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
            new ApplicationSystemEntity("sample", "同名但未持久化的另一系统"), "GET", "/__t02__/other");
    permission.mapApi(api);
    permission.mapApi(api);
    assertThrows(IllegalArgumentException.class, () -> permission.mapApi(other));
    assertEquals(1, permission.getApis().size());
    assertEquals("GET", api.getHttpMethod());
    permission.unmapApi(api);
    assertTrue(permission.getApis().isEmpty());
  }

  /** 暴露的关联集合只读，调用者不能直接绕过数据一致性检查。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 暴露的关联集合只读，调用者不能直接绕过数据一致性检查。")
  void associationCollectionsCannotBypassDomainWriteMethods() {
    DepartmentEntity department = new DepartmentEntity("d", "部门");
    UserEntity user = new UserEntity("u", "existing-hash", "用户", department, true);
    RoleEntity role = new RoleEntity("r", "角色");
    PermissionEntity permission = permission("p", MENU);
    ApiResourceEntity api = new ApiResourceEntity(system, "GET", "/__t02__/sample");
    user.assignRole(role);
    user.assignRole(role);
    role.grantPermission(permission);
    role.grantPermission(permission);
    assertEquals(1, user.getRoles().size());
    assertEquals(1, role.getPermissions().size());
    assertThrows(UnsupportedOperationException.class, () -> user.getRoles().clear());
    assertThrows(UnsupportedOperationException.class, () -> role.getPermissions().clear());
    assertThrows(UnsupportedOperationException.class, () -> permission.getApis().add(api));
    assertThrows(
        UnsupportedOperationException.class, () -> permission.getChildren().add(permission));
    user.removeRole(role);
    role.revokePermission(permission);
    assertTrue(user.getRoles().isEmpty());
    assertTrue(role.getPermissions().isEmpty());
  }

  /** 必需字段和关联缺失时立即拒绝创建非法持久化记录。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 必需字段和关联缺失时立即拒绝创建非法持久化记录。")
  void requiredFieldsAndAssociationsRejectMissingValues() {
    DepartmentEntity department = new DepartmentEntity("d", "部门");
    assertThrows(IllegalArgumentException.class, () -> new DepartmentEntity(" ", "部门"));
    assertThrows(IllegalArgumentException.class, () -> new ApplicationSystemEntity("s", " "));
    assertThrows(IllegalArgumentException.class, () -> new RoleEntity("", "角色"));
    assertThrows(IllegalArgumentException.class, () -> new PermissionEntity(null, "p", "权限", MENU));
    assertThrows(IllegalArgumentException.class, () -> permission("p", null));
    assertThrows(
        IllegalArgumentException.class, () -> new ApiResourceEntity(null, "GET", "/sample"));
    assertThrows(
        IllegalArgumentException.class, () -> new ApiResourceEntity(system, " ", "/sample"));
    assertThrows(
        IllegalArgumentException.class, () -> new UserEntity("u", "", "用户", department, true));
    assertThrows(
        IllegalArgumentException.class, () -> new UserEntity("u", "hash", "用户", null, true));
    UserEntity user = new UserEntity("u", "hash", "用户", department, true);
    assertThrows(IllegalArgumentException.class, () -> user.moveTo(null));
    assertThrows(IllegalArgumentException.class, () -> user.assignRole(null));
    assertThrows(
        IllegalArgumentException.class, () -> new RoleEntity("r", "角色").grantPermission(null));
    assertThrows(IllegalArgumentException.class, () -> permission("p", MENU).mapApi(null));
    assertThrows(IllegalArgumentException.class, () -> permission("p", MENU).changeType(null));
  }

  /** 模型建删表测试不能连接旧库、外部主机或未知数据库。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 模型建删表测试不能连接旧库、外部主机或未知数据库。")
  void destructiveSchemaTestsRejectLegacyOrUnexpectedDatabaseUrls() {
    for (String url :
        List.of(
            "",
            "jdbc:h2:mem:rbac_core_t02_test",
            "jdbc:mysql://127.0.0.1:3317/rbac",
            "jdbc:mysql://127.0.0.1:3317/rbac_test",
            "jdbc:mysql://example.com:3317/rbac_core_t02_test",
            "jdbc:mysql://127.0.0.1:3317/rbac_core_t02_test_backup")) {
      assertThrows(
          IllegalArgumentException.class,
          () -> R01JpaConstraintTest.ModelConfiguration.requireIsolatedDatabase(url));
    }
    assertDoesNotThrow(
        () ->
            R01JpaConstraintTest.ModelConfiguration.requireIsolatedDatabase(
                "jdbc:mysql://127.0.0.1:3317/rbac_core_t02_test?connectionTimeZone=UTC"));
  }
}
