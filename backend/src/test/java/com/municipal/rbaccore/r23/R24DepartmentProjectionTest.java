package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.ddd.application.support.DddDepartmentView;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.port.security.RbacCatalog.Route;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R24纯读模型组合测试；统计端口和路由端口提供明确输入，不宣称执行SQL。 */
class R24DepartmentProjectionTest {
  private static final Route CREATE = new Route("POST", "/api/departments");
  private static final Route EDIT = new Route("PATCH", "/api/departments/{id}");
  private static final Route DELETE = new Route("DELETE", "/api/departments/{id}");
  private final Department root = new Department(1L, "root", "总公司", null, Department.Type.HEADQUARTERS, false);
  private final Department child = new Department(2L, "child", "部门", 1L, Department.Type.DEPARTMENT, false);
  private final Department project = new Department(3L, "project", "项目", 2L, Department.Type.PROJECT, false);

  /** 可见树缺少本人节点时不能返回空成功，更不能显示其他部门。 */
  @Test
  @DisplayName("R24 组织事实缺少本人节点时拒绝构造部门树")
  void R24_missingOwnDepartmentRejectsProjection() {
    UserRepository users = mock(UserRepository.class);
    when(users.statistics(any())).thenReturn(new OrganizationFacts.Statistics(0, 0, List.of()));
    ManagementAuthorization authorization = mock(ManagementAuthorization.class);
    User actor = person(new Department(9L, "missing", "未读取到", null, Department.Type.DEPARTMENT, false), 3);
    assertEquals("MANAGEMENT_SCOPE_DENIED", assertThrows(BusinessException.class,
        () -> new DddDepartmentView(users, authorization).build(actor,
            new OrganizationFacts(1L, List.of(root, child)))).getCode());
    verifyNoInteractions(authorization);
  }

  /** 每项真实路由能力独立决定对应按钮，不能因级别为5就全部启用。 */
  @Test
  @DisplayName("R24 按钮分别受创建改名删除路由控制，根和非空节点不能删除")
  void R24_routesAndOccupancyIndependentlyControlButtons() {
    OrganizationFacts facts = new OrganizationFacts(1L, List.of(root, child, project));
    for (Set<Route> routes : List.of(Set.<Route>of(), Set.of(CREATE), Set.of(EDIT), Set.of(DELETE))) {
      DddDepartmentView.View view = view(root.id(), routes).build(person(root, 5), facts);
      assertEquals(List.of(1L, 2L, 3L), view.tree().stream().map(row -> row.getId()).toList());
      assertEquals(routes.contains(CREATE), view.tree().getFirst().getCanCreate());
      assertEquals(routes.contains(EDIT), view.tree().getFirst().getCanEdit());
      assertFalse(view.tree().getFirst().getCanDelete());
      assertFalse(view.tree().get(1).getCanDelete(), "有下级的空部门仍不能删除");
      assertFalse(view.tree().get(2).getCanCreate(), "项目没有下级");
      assertEquals(routes.contains(DELETE), view.tree().get(2).getCanDelete());
    }
  }

  /** 局部范围只能将本人节点投影为根，异常总公司类型也不能成为可信根。 */
  @Test
  @DisplayName("R24 非可信根的总公司类型不显示新增能力且保留本人部门保护")
  void R24_untrustedHeadquartersTypeCannotAcquireRootCapabilities() {
    Department untrusted = new Department(2L, "legacy", "旧类型", 1L, Department.Type.HEADQUARTERS, false);
    DddDepartmentView.View result = view(2L, Set.of(CREATE, EDIT, DELETE)).build(person(untrusted, 5),
        new OrganizationFacts(1L, List.of(root, untrusted)));
    assertEquals(1, result.tree().size());
    assertNull(result.tree().getFirst().getParentId());
    assertFalse(result.tree().getFirst().getCanCreate());
    assertFalse(result.tree().getFirst().getCanEdit());
    assertFalse(result.tree().getFirst().getCanDelete());
  }

  /** 统计事实包含当前用户一次，空子节点只依赖明确的占用事实。 */
  private DddDepartmentView view(Long own, Set<Route> routes) {
    UserRepository users = mock(UserRepository.class);
    when(users.statistics(any())).thenReturn(new OrganizationFacts.Statistics(1, 1,
        List.of(new OrganizationFacts.Count(own, 1))));
    ManagementAuthorization authorization = mock(ManagementAuthorization.class);
    when(authorization.routes(1L)).thenReturn(routes);
    return new DddDepartmentView(users, authorization);
  }

  /** 管理级别只由启用基础角色贡献，不用角色名称伪造级别。 */
  private User person(Department department, int level) {
    Role role = new Role(1L, "base", "基础角色", level, true, Set.of());
    return new User(1L, "sample", "not-used", "示例", null, null, department, true, Set.of(role));
  }
}
