package com.municipal.rbaccore.api.course;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.course.dto.CourseLoginDTO;
import com.municipal.rbaccore.api.course.vo.CourseIdentityVO;
import com.municipal.rbaccore.api.course.vo.CourseLoginVO;
import com.municipal.rbaccore.api.course.vo.CoursePermissionVO;
import com.municipal.rbaccore.api.course.vo.CourseUserVO;
import com.municipal.rbaccore.api.shared.RawResponse;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.course.CourseIdentityResult;
import com.municipal.rbaccore.service.port.course.CourseIdentityUseCase;
import com.municipal.rbaccore.service.port.course.CourseLoginResult;
import com.municipal.rbaccore.service.port.course.CourseUserResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 适配老师已发布的身份HTTP协议，不承载任何课程业务接口。 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@RawResponse
@ConditionalOnProperty(name = "app.course-integration.enabled", havingValue = "true")
public class CourseIdentityController {
  private final CourseIdentityUseCase service;

  /** 同名口令由真实处理器复制，账号协议异名显式转换。 */
  @PostMapping("/login")
  public CourseLoginVO login(
  @Valid
  @RequestBody
  CourseLoginDTO dto) {
    LoginCommand command = CloneFactory.copy(new LoginCommand(), dto);
    command.setAccount(dto.getUsername());
    CourseLoginResult result = service.login(command);
    return new CourseLoginVO(result.accessToken(), result.tokenType(), result.expiresIn());
  }

  /** record为不可变协议对象，显式构造而不依赖处理器写入final字段。 */
  @GetMapping("/me")
  public CourseIdentityVO me(
      @RequestHeader(value = "Authorization", required = false)
      String header) {
    CourseIdentityResult result = service.current(header);
    return new CourseIdentityVO(result.subject(), result.roles(), result.permissions());
  }

  /** 未登记或已失效短码返回拒绝，未知短码不会隐式变为通配权限。 */
  @GetMapping("/permissions/{permission}")
  public CoursePermissionVO permission(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Size(max = 191)
      String permission) {
    return new CoursePermissionVO(service.allowed(header, permission));
  }

  /** 对外字符串身份按本项目整数主键解析，查询授权由用例处理。 */
  @GetMapping("/users/{userId}")
  public CourseUserVO user(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      Long userId) {
    CourseUserResult result = service.user(header, userId);
    return new CourseUserVO(result.userId(), result.no(), result.name());
  }
}
