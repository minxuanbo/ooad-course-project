package com.municipal.rbaccore.r18;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.audit.AuditController;
import com.municipal.rbaccore.api.audit.dto.LogQueryDTO;
import com.municipal.rbaccore.api.department.dto.DepartmentCreateDTO;
import com.municipal.rbaccore.api.department.dto.DepartmentRenameDTO;
import com.municipal.rbaccore.api.resource.dto.ResourceApiQueryDTO;
import com.municipal.rbaccore.api.role.support.RoleText;
import com.municipal.rbaccore.api.user.UserController;
import com.municipal.rbaccore.api.user.dto.UserQueryDTO;
import com.municipal.rbaccore.api.user.dto.UserRolesDTO;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.service.port.audit.*;
import com.municipal.rbaccore.service.port.department.CreateDepartmentCommand;
import com.municipal.rbaccore.service.port.department.RenameDepartmentCommand;
import com.municipal.rbaccore.service.port.resource.UpdateApiCommand;
import com.municipal.rbaccore.service.port.user.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** R18区分省略、显式false和空集合，映射与校验不能擅自改变请求语义。 */
class R18OptionalInputBoundaryTest {
  /** 不换行空白与null均按接口清洗规则处理，必填错误留给验证器。 */
  @Test
  @DisplayName("R18 可选搜索保持null，必填部门名称和角色集合仍拒绝缺失")
  void R18_preservesMissingInputsAndUnicodeBoundaries() {
    assertNull(RoleText.strip(null));
    assertEquals("甲\u00a0乙", RoleText.strip("\u00a0甲\u00a0乙\u00a0"));
    assertEquals("甲", RoleText.strip("\u2003\uFEFF甲\uFEFF\u2003"));
    UserQueryDTO users = new UserQueryDTO();
    users.setQ(null);
    ResourceApiQueryDTO apis = new ResourceApiQueryDTO();
    apis.setQ(null);
    DepartmentCreateDTO create = new DepartmentCreateDTO();
    create.setName(null);
    DepartmentRenameDTO rename = new DepartmentRenameDTO();
    rename.setName(null);
    UserRolesDTO roles = new UserRolesDTO();
    assertTrue(roles.isUniqueIds());
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      assertTrue(factory.getValidator().validate(users).isEmpty());
      assertTrue(factory.getValidator().validate(apis).isEmpty());
      assertFalse(factory.getValidator().validate(create).isEmpty());
      assertFalse(factory.getValidator().validate(rename).isEmpty());
      assertFalse(factory.getValidator().validate(roles).isEmpty());
    }
    LogQueryDTO query = new LogQueryDTO();
    query.setFrom("2026-11-23T00:00:00Z");
    assertTrue(query.isTimeRangeValid(), "只提供起点允许查询后续全部记录");
  }

  /** Controller仅转换Boolean三态，不能把未筛选误转成false。 */
  @Test
  @DisplayName("R18 人员与日志控制器保留省略的状态筛选")
  void R18_controllersKeepUnspecifiedBooleanFilters() {
    UserQueryUseCase users = mock(UserQueryUseCase.class);
    when(users.list(any(), any())).thenReturn(new UserPageResult(List.of(), 0, 1, 20));
    UserQueryDTO query = new UserQueryDTO();
    new UserController(users, mock(UserManagementUseCase.class)).users("Bearer test", query);
    ArgumentCaptor<UserQuery> userQuery = ArgumentCaptor.forClass(UserQuery.class);
    verify(users).list(eq("Bearer test"), userQuery.capture());
    assertNull(userQuery.getValue().getEnabled());
    query.setEnabled("false");
    new UserController(users, mock(UserManagementUseCase.class)).users("Bearer test", query);
    verify(users, times(2)).list(eq("Bearer test"), userQuery.capture());
    assertEquals(Boolean.FALSE, userQuery.getValue().getEnabled());
    AuditLogUseCase logs = mock(AuditLogUseCase.class);
    when(logs.list(any(), any())).thenReturn(new LogPageResult(List.of(), null, false, 0, 0, List.of()));
    assertTrue(new AuditController(logs).list("Bearer test", new LogQueryDTO()).getItems().isEmpty());
    ArgumentCaptor<LogQuery> logQuery = ArgumentCaptor.forClass(LogQuery.class);
    verify(logs).list(eq("Bearer test"), logQuery.capture());
    assertNull(logQuery.getValue().getAllowed());
    LogQueryDTO denied = new LogQueryDTO();
    denied.setAllowed("false");
    new AuditController(logs).list("Bearer test", denied);
    verify(logs, times(2)).list(eq("Bearer test"), logQuery.capture());
    assertEquals(Boolean.FALSE, logQuery.getValue().getAllowed());
  }

  /** 真实生成器的非空复制应保留原属性，不能靠空映射伪装成正确补丁。 */
  @Test
  @DisplayName("R19 缺省API和部门字段经真实CloneFactory保留原属性")
  void R19_generatedPatchesRetainOmittedValues() {
    ApplicationSystemEntity system = new ApplicationSystemEntity("rbac", "权限系统");
    ApiResourceEntity api = new ApiResourceEntity(system, "GET", "/items");
    CloneFactory.copyNotNull(api, new UpdateApiCommand());
    assertTrue(api.isEnabled());
    assertEquals("/items", api.getPathTemplate());
    DepartmentEntity department = new DepartmentEntity("stable", "原名称");
    CloneFactory.copyNotNull(department, new CreateDepartmentCommand());
    CloneFactory.copyNotNull(department, new RenameDepartmentCommand());
    assertEquals("stable", department.getCode());
    assertEquals("原名称", department.getName());
    assertEquals(com.municipal.rbaccore.service.port.department.DepartmentType.DEPARTMENT, department.getType());
  }
}
