package com.municipal.rbaccore.service.structured.course;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.course.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.IdentityTokenStore;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化课程身份事务脚本，直接读取JPA事实，不复用DDD领域实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.course-integration.enabled", havingValue = "true")
public class StructuredCourseIdentityService implements CourseIdentityUseCase {
  private final AuthenticationUseCase authentication;
  private final IdentityTokenStore tokens;
  private final PermissionJpaRepository permissions;
  private final UserJpaRepository users;

  /** 课程教师准入和RBAC管理准入分开，使用同一账号凭据验证。 */
  @Override
  public CourseLoginResult login(LoginCommand command) {
    CurrentIdentity actor = authentication.verify(command);
    currentPermissions(actor.id());
    return new CourseLoginResult(
        tokens.issueForSystem(actor.id(), CourseContract.SYSTEM_CODE),
        "Bearer",
        tokens.expiresIn());
  }

  /** 当前权限不放入令牌，停用与撤回均在下一个请求生效。 */
  @Override
  public CourseIdentityResult current(String header) {
    CurrentIdentity actor = authentication.requireForSystem(header, CourseContract.SYSTEM_CODE);
    return new CourseIdentityResult(
        actor.id().toString(),
        List.of(CourseContract.TEACHER_ROLE),
        currentPermissions(actor.id()).stream()
            .filter(code -> !CourseContract.TEACHER.equals(code))
            .sorted()
            .toList());
  }

  /** 菜单和按钮检查只返回协议中当前有效的短码。 */
  @Override
  public boolean allowed(String header, String code) {
    return current(header).permissions().contains(code);
  }

  /** 教师查询仅导出必要字段，权限不足时不暴露对象是否存在。 */
  @Override
  public CourseUserResult user(String header, Long id) {
    if (!allowed(header, "UL")) throw BusinessException.forbidden();
    UserEntity user =
        users
            .findById(id)
            .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "用户不存在"));
    return new CourseUserResult(user.getId().toString(), user.getUsername(), user.getName());
  }

  /** 同系统有效链与该短码全部预期路由均成立才导出后端能力。 */
  private Set<String> currentPermissions(Long id) {
    Set<String> result = new TreeSet<>();
    for (PermissionEntity permission :
        permissions.directPermissions(id, Set.of(CourseContract.SYSTEM_CODE))) {
      if (CourseContract.DISPLAY_CODES.contains(permission.getCode()))
        result.add(permission.getCode());
      List<RbacCatalog.Route> required = CourseContract.BACKEND_ROUTES.get(permission.getCode());
      if (required == null || required.isEmpty()) continue;
      Set<RbacCatalog.Route> actual =
          permission.getApis().stream()
              .filter(
                  api ->
                      api.isEnabled()
                          && api.getSystem().isEnabled()
                          && api.getSystem().getId().equals(permission.getSystem().getId()))
              .map(this::route)
              .collect(Collectors.toUnmodifiableSet());
      if (actual.containsAll(required)) result.add(permission.getCode());
    }
    if (!result.contains(CourseContract.TEACHER)) {
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "该账号没有课程系统的有效教师准入");
    }
    return result;
  }

  /** 实体字段与方法/模板协议异名，显式构造不可变路由。 */
  private RbacCatalog.Route route(ApiResourceEntity api) {
    return new RbacCatalog.Route(api.getHttpMethod(), api.getPathTemplate());
  }
}
