package com.municipal.rbaccore.r06;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 首次目录必须允许同一页面的各操作独立授予，不能默认捆绑多个接口。 */
class R06CatalogGranularityTest {

  /** 原型中的单操作权限落实为真实API一对一映射，维护恢复集合保持不变。 */
  @Test
  @DisplayName("R06/R14/R34 操作与登录独立授权，30个资源不遗漏不重叠且完整K仍为17")
  void R06_separatesEveryManagementOperation() {
    Set<RbacCatalog.Route> unique = new HashSet<>();
    RbacCatalog.definitions()
        .forEach(
            (code, routes) -> {
              assertEquals(1, routes.size(), code + "不能默认捆绑其他按钮接口");
              assertTrue(unique.add(routes.getFirst()), code + "与其他默认权限重复映射");
            });
    assertEquals(30, unique.size());
    assertEquals(17, RbacCatalog.maintenanceRoutes().size());
    assertEquals(
        Set.of(new RbacCatalog.Route("GET", "/api/users")),
        Set.copyOf(RbacCatalog.definitions().get("rbac.users.read")));
  }
}
