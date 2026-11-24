package com.municipal.rbaccore.architecture;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.resource.dto.ResourcePermissionPatchDTO;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.service.port.resource.UpdatePermissionCommand;
import com.municipal.rbaccore.service.port.role.UpdateRoleCommand;
import com.municipal.rbaccore.api.identity.vo.*;
import com.municipal.rbaccore.service.port.identity.*;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R19验证真实课程处理器生成的映射，不用反射复制或模拟替代。 */
class R19CloneFactoryMappingTest {
  /** 新版构造器映射须真正创建完整record，不能因final字段而返回空对象或漏字段。 */
  @Test
  @DisplayName("R19/R32 CloneFactory0.0.2完整构造五种不可变身份协议响应")
  void R19R32_constructsImmutableIdentityResponses() {
    Map<String, List<RbacCatalog.Route>> routes =
        Map.of("view", List.of(new RbacCatalog.Route("GET", "/sample")));
    Object[][] cases = {
      {ApplicationLoginVO.class, ApplicationLoginResult.class,
          new ApplicationLoginResult("fixture-token", "Bearer", 43200),
          new ApplicationLoginVO("fixture-token", "Bearer", 43200)},
      {ApplicationIdentityVO.class, ApplicationIdentityResult.class,
          new ApplicationIdentityResult("37", List.of("reader"), List.of("view"), "sample", routes),
          new ApplicationIdentityVO("37", List.of("reader"), List.of("view"), "sample", routes)},
      {ApplicationUserVO.class, ApplicationUserResult.class,
          new ApplicationUserResult("37", "00037", "映射员工"),
          new ApplicationUserVO("37", "00037", "映射员工")},
      {ApplicationDepartmentVO.class, ApplicationDepartmentResult.class,
          new ApplicationDepartmentResult(9L, "D09", "映射部门", 2L, true),
          new ApplicationDepartmentVO(9L, "D09", "映射部门", 2L, true)},
      {ApplicationDepartmentUserVO.class, ApplicationDepartmentUserResult.class,
          new ApplicationDepartmentUserResult("37", "00037", "映射员工", 9L, "映射部门"),
          new ApplicationDepartmentUserVO("37", "00037", "映射员工", 9L, "映射部门")}
    };
    for (Object[] sample : cases) {
      Object actual = assertDoesNotThrow(() ->
          CloneFactory.class.getMethod("copy", (Class<?>) sample[0], (Class<?>) sample[1])
              .invoke(null, null, sample[2]));
      assertEquals(sample[3], actual, ((Class<?>) sample[0]).getSimpleName());
    }
  }
  /** PATCH只复制非空字段，编码和未提供状态保持原值。 */
  @Test
  @DisplayName("R13/R19 角色PATCH通过真实CloneFactory保留缺省字段")
  void R13R19_rolePatchPreservesOmittedFields() {
    RoleEntity role = new RoleEntity("stable", "原名称");
    UpdateRoleCommand command = new UpdateRoleCommand();
    command.setName("新名称");
    assertDoesNotThrow(() -> applyRolePatch(role, command));
    assertEquals("stable", role.getCode());
    assertEquals("新名称", role.getName());
    assertTrue(role.isEnabled());
    command.setName(null);
    command.setEnabled(false);
    assertDoesNotThrow(() -> applyRolePatch(role, command));
    assertEquals("新名称", role.getName());
    assertFalse(role.isEnabled());
  }

  /** 检查处理器实际生成的方法和行为，缺少映射时产生可读的行为失败。 */
  private void applyRolePatch(RoleEntity role, UpdateRoleCommand command) throws Exception {
    CloneFactory.class
        .getMethod("copyNotNull", RoleEntity.class, UpdateRoleCommand.class)
        .invoke(null, role, command);
  }

  /** parentId显式null不能被当成缺省过滤掉，复制只负责携带存在性。 */
  @Test
  @DisplayName("R14/R19 权限PATCH保留父级缺省与显式清空三态")
  void R14R19_permissionPatchRetainsPresence() {
    ResourcePermissionPatchDTO omitted = new ResourcePermissionPatchDTO();
    omitted.setEnabled(false);
    UpdatePermissionCommand first = CloneFactory.copy(new UpdatePermissionCommand(), omitted);
    assertFalse(first.getParentIdPresent());
    ResourcePermissionPatchDTO clear = new ResourcePermissionPatchDTO();
    clear.setParentId(null);
    UpdatePermissionCommand second = CloneFactory.copy(new UpdatePermissionCommand(), clear);
    assertTrue(second.getParentIdPresent());
    assertNull(second.getParentId());
    assertNull(second.getEnabled());
  }
}
