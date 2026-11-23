package com.municipal.rbaccore.infrastructure.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.port.resource.PermissionType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** R01测试实体自身的长度及持久化身份不变量，数据库约束由独立MySQL测试验证。 */
class R01EntityBoundaryTest {
  /** 非法长输入不能通过截断入库，资料更新失败也不能部分生效。 */
  @Test
  @DisplayName("R01 用户字段超过物理上限时拒绝，失败保留原资料")
  void R01_rejectsOversizedUserFieldsAtomically() {
    DepartmentEntity department = new DepartmentEntity("department", "部门");
    assertThrows(IllegalArgumentException.class,
        () -> new UserEntity("u".repeat(101), "hash", "姓名", department, true));
    assertThrows(IllegalArgumentException.class,
        () -> new UserEntity("user", "h".repeat(101), "姓名", department, true));
    assertThrows(IllegalArgumentException.class,
        () -> new UserEntity("user", "hash", "名".repeat(101), department, true));
    UserEntity user = new UserEntity("user", "hash", "姓名", department, true);
    user.updateProfile("00123", "岗位");
    assertThrows(IllegalArgumentException.class, () -> user.updateProfile("1".repeat(33), "新岗位"));
    assertThrows(IllegalArgumentException.class, () -> user.updateProfile("00456", "职".repeat(192)));
    assertEquals("00123", user.getPhone());
    assertEquals("岗位", user.getPosition());
  }

  /** 同名不代表同实体；不同JPA实例仍按持久化主键判断同一身份。 */
  @Test
  @DisplayName("R01 系统身份按持久化ID比较，null和同名新对象不冒充同实体")
  void R01_applicationIdentityUsesPersistedId() {
    ApplicationSystemEntity first = new ApplicationSystemEntity("rbac", "权限系统");
    ApplicationSystemEntity second = new ApplicationSystemEntity("rbac", "同名来源");
    assertFalse(first.sameIdentity(null));
    assertTrue(first.sameIdentity(first));
    assertFalse(first.sameIdentity(second));
    ReflectionTestUtils.setField(first, "id", 1L);
    ReflectionTestUtils.setField(second, "id", 1L);
    assertTrue(first.sameIdentity(second));
    ReflectionTestUtils.setField(second, "id", 2L);
    assertFalse(first.sameIdentity(second));
  }

  /** 不同Java实例的自指主键及上游既有循环都要拒绝，防止查询死循环。 */
  @Test
  @DisplayName("R01 部门和权限按数据库身份拒绝自指及损坏的上游循环")
  void R01_rejectsDetachedIdentityCyclesAndCorruptedParents() {
    DepartmentEntity current = new DepartmentEntity("current", "当前");
    DepartmentEntity same = new DepartmentEntity("same", "同一持久化记录");
    ReflectionTestUtils.setField(current, "id", 1L);
    ReflectionTestUtils.setField(same, "id", 1L);
    assertThrows(IllegalArgumentException.class, () -> current.moveTo(same));
    DepartmentEntity a = new DepartmentEntity("a", "甲");
    DepartmentEntity b = new DepartmentEntity("b", "乙");
    ReflectionTestUtils.setField(a, "parent", b);
    ReflectionTestUtils.setField(b, "parent", a);
    assertThrows(IllegalArgumentException.class, () -> current.moveTo(a));
    assertNull(current.getParent());
    ApplicationSystemEntity system = new ApplicationSystemEntity("rbac", "权限系统");
    PermissionEntity permission = new PermissionEntity(system, "p", "当前", PermissionType.MENU);
    PermissionEntity duplicate = new PermissionEntity(system, "copy", "同一记录", PermissionType.MENU);
    ReflectionTestUtils.setField(permission, "id", 1L);
    ReflectionTestUtils.setField(duplicate, "id", 1L);
    assertThrows(IllegalArgumentException.class, () -> permission.moveTo(duplicate));
    PermissionEntity parentA = new PermissionEntity(system, "a", "甲", PermissionType.MENU);
    PermissionEntity parentB = new PermissionEntity(system, "b", "乙", PermissionType.MENU);
    ReflectionTestUtils.setField(parentA, "parent", parentB);
    ReflectionTestUtils.setField(parentB, "parent", parentA);
    assertThrows(IllegalArgumentException.class, () -> permission.moveTo(parentA));
    assertNull(permission.getParent());
  }
}
