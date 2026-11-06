package com.municipal.rbaccore;

import com.municipal.rbaccore.model.*;
import static com.municipal.rbaccore.model.Permission.Type.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreModelRulesTest {
  private final ApplicationSystem system = new ApplicationSystem("sample", "样例系统");

  private Permission permission(String code, Permission.Type type) {
    return new Permission(system, code, code, type);
  }

  @Test void departmentTreesRejectSelfAndCyclesBeforeChangingParent() {
    var root = new Department("root", "根");
    var child = new Department("child", "子");
    var leaf = new Department("leaf", "叶");
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

  @Test void permissionTreesRejectSelfCyclesAndCrossSystemParentsWithoutUnlinking() {
    var root = permission("root", MENU);
    var child = permission("child", MENU);
    var leaf = permission("leaf", MENU);
    child.moveTo(root);
    leaf.moveTo(child);
    assertThrows(IllegalArgumentException.class, () -> child.moveTo(child));
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(leaf));
    var other = new Permission(new ApplicationSystem("other", "其他"), "other", "其他", MENU);
    assertThrows(IllegalArgumentException.class, () -> child.moveTo(other));
    assertSame(root, child.getParent());
    assertEquals(List.of(child), root.getChildren().stream().toList());
    assertNull(other.getParent());
    assertTrue(other.getChildren().isEmpty());
  }

  @Test void buttonCannotBeParentAndMovingChildrenKeepsTypeChecksCurrent() {
    var oldParent = permission("old", MENU);
    var newParent = permission("new", MENU);
    var button = permission("button", BUTTON);
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

  @Test void apiMappingsRequireSameSystemAndRejectBeforeMutating() {
    var permission = permission("read", BUTTON);
    var api = new ApiResource(system, " get ", "/__t02__/sample");
    var other = new ApiResource(new ApplicationSystem("sample", "同名但未持久化的另一系统"),
        "GET", "/__t02__/other");
    permission.mapApi(api);
    permission.mapApi(api);
    assertThrows(IllegalArgumentException.class, () -> permission.mapApi(other));
    assertEquals(1, permission.getApis().size());
    assertEquals("GET", api.getHttpMethod());
    permission.unmapApi(api);
    assertTrue(permission.getApis().isEmpty());
  }

  @Test void associationCollectionsCannotBypassDomainWriteMethods() {
    var department = new Department("d", "部门");
    var user = new User("e", "u", "existing-hash", "用户", department, true);
    var role = new Role("r", "角色");
    var permission = permission("p", MENU);
    var api = new ApiResource(system, "GET", "/__t02__/sample");
    user.assignRole(role);
    user.assignRole(role);
    role.grantPermission(permission);
    role.grantPermission(permission);
    assertEquals(1, user.getRoles().size());
    assertEquals(1, role.getPermissions().size());
    assertThrows(UnsupportedOperationException.class, () -> user.getRoles().clear());
    assertThrows(UnsupportedOperationException.class, () -> role.getPermissions().clear());
    assertThrows(UnsupportedOperationException.class, () -> permission.getApis().add(api));
    assertThrows(UnsupportedOperationException.class, () -> permission.getChildren().add(permission));
    user.removeRole(role);
    role.revokePermission(permission);
    assertTrue(user.getRoles().isEmpty());
    assertTrue(role.getPermissions().isEmpty());
  }

  @Test void requiredFieldsAndAssociationsRejectMissingValues() {
    var department = new Department("d", "部门");
    assertThrows(IllegalArgumentException.class, () -> new Department(" ", "部门"));
    assertThrows(IllegalArgumentException.class, () -> new ApplicationSystem("s", " "));
    assertThrows(IllegalArgumentException.class, () -> new Role("", "角色"));
    assertThrows(IllegalArgumentException.class, () -> new Permission(null, "p", "权限", MENU));
    assertThrows(IllegalArgumentException.class, () -> permission("p", null));
    assertThrows(IllegalArgumentException.class, () -> new ApiResource(null, "GET", "/sample"));
    assertThrows(IllegalArgumentException.class, () -> new ApiResource(system, " ", "/sample"));
    assertThrows(IllegalArgumentException.class, () -> new User("e", "u", "", "用户", department, true));
    assertThrows(IllegalArgumentException.class, () -> new User("e", "u", "hash", "用户", null, true));
    var user = new User("e", "u", "hash", "用户", department, true);
    assertThrows(IllegalArgumentException.class, () -> user.moveTo(null));
    assertThrows(IllegalArgumentException.class, () -> user.assignRole(null));
    assertThrows(IllegalArgumentException.class, () -> new Role("r", "角色").grantPermission(null));
    assertThrows(IllegalArgumentException.class, () -> permission("p", MENU).mapApi(null));
    assertThrows(IllegalArgumentException.class, () -> permission("p", MENU).changeType(null));
  }

  @Test void destructiveSchemaTestsRejectLegacyOrUnexpectedDatabaseUrls() {
    for (String url : List.of("", "jdbc:h2:mem:rbac_core_t02_test",
        "jdbc:mysql://127.0.0.1:3317/rbac",
        "jdbc:mysql://127.0.0.1:3317/rbac_test",
        "jdbc:mysql://example.com:3317/rbac_core_t02_test",
        "jdbc:mysql://127.0.0.1:3317/rbac_core_t02_test_backup")) {
      assertThrows(IllegalArgumentException.class,
          () -> CoreModelJpaTest.ModelConfiguration.requireIsolatedDatabase(url));
    }
    assertDoesNotThrow(() -> CoreModelJpaTest.ModelConfiguration.requireIsolatedDatabase(
        "jdbc:mysql://127.0.0.1:3317/rbac_core_t02_test?connectionTimeZone=UTC"));
  }
}
