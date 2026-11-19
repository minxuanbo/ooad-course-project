package com.municipal.rbaccore.service.ddd.application.course;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.model.policy.CourseAuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.course.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.IdentityTokenStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DDD课程身份用例只依赖领域仓储及共享端口，授权计算归纯领域策略。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.course-integration.enabled", havingValue = "true")
public class DddCourseIdentityService implements CourseIdentityUseCase {
  private final AuthenticationUseCase authentication;
  private final IdentityTokenStore tokens;
  private final AuthorizationRepository authorization;
  private final UserRepository users;

  /** 核实同一账号后检查课程领域准入，再签发课程专用受众身份。 */
  @Override
  public CourseLoginResult login(LoginCommand command) {
    CurrentIdentity actor = authentication.verify(command);
    currentPermissions(actor.id());
    return new CourseLoginResult(
        tokens.issueForSystem(actor.id(), CourseContract.SYSTEM_CODE),
        "Bearer",
        tokens.expiresIn());
  }

  /** 每次装配新领域事实，角色启停及映射修改不等待重新登录。 */
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

  /** 当前短码查询不接受客户端自报的角色或授权结论。 */
  @Override
  public boolean allowed(String header, String code) {
    return current(header).permissions().contains(code);
  }

  /** 课程UL许可通过后读取最小领域用户投影。 */
  @Override
  public CourseUserResult user(String header, Long id) {
    if (!allowed(header, "UL")) throw BusinessException.forbidden();
    User user =
        users
            .find(new User.Key(id))
            .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "用户不存在"));
    return new CourseUserResult(user.id().toString(), user.username(), user.name());
  }

  /** 将固定外部协议转换为领域路由值，领域不依赖服务端口中的协议类型。 */
  private Set<String> currentPermissions(Long id) {
    AuthorizationFacts facts =
        authorization.facts(new User.Key(id)).orElseThrow(BusinessException::unauthorized);
    Map<String, Set<ApiResource.Route>> routes =
        CourseContract.BACKEND_ROUTES.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().stream()
                            .map(route -> new ApiResource.Route(route.method(), route.path()))
                            .collect(Collectors.toUnmodifiableSet())));
    Set<String> result =
        CourseAuthorizationPolicy.exportedPermissions(
            facts, CourseContract.SYSTEM_CODE, routes, CourseContract.DISPLAY_CODES);
    if (!result.contains(CourseContract.TEACHER)) {
      throw new BusinessException(403, "APPLICATION_ACCESS_DENIED", "该账号没有课程系统的有效教师准入");
    }
    return result;
  }
}
