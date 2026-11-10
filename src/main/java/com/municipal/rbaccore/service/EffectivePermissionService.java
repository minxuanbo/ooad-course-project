package com.municipal.rbaccore.service;

import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.repository.ApiResourceRepository;
import com.municipal.rbaccore.repository.PermissionRepository;
import com.municipal.rbaccore.security.RbacCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录准入、实际API和本人权限共用的有效授权计算。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EffectivePermissionService {
  private final ApiResourceRepository apis;
  private final PermissionRepository permissions;

  /** 当前账号的有效API按完整同系统启用链计算。 */
  public Set<RbacCatalog.Route> routes(Long userId) {
    return apis.effectiveApis(userId, RbacCatalog.SYSTEM_CODE).stream()
        .map(api -> new RbacCatalog.Route(api.getHttpMethod(), api.getPathTemplate()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 至少一个已评审管理API有效可达；菜单、角色名和外系统权限不构成准入。 */
  public boolean canEnter(Long userId) {
    return routes(userId).stream().anyMatch(RbacCatalog.managementRoutes()::contains);
  }

  /** 后端只匹配服务端识别的方法与模板，不依赖前端权限参数。 */
  public boolean allows(Long userId, String method, String template) {
    return routes(userId).contains(new RbacCatalog.Route(method, template));
  }

  /** 导航不补入未实际授予的祖先权限。 */
  public List<Permission> permissions(Long userId) {
    return permissions.effectivePermissions(userId, RbacCatalog.SYSTEM_CODE);
  }
}
