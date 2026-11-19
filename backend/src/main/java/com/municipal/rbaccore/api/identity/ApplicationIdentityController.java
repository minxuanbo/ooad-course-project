package com.municipal.rbaccore.api.identity;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.identity.dto.ApplicationLoginDTO;
import com.municipal.rbaccore.api.identity.vo.ApplicationIdentityVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationLoginVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationPermissionVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationUserVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.api.shared.RawResponse;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.identity.ApplicationIdentityResult;
import com.municipal.rbaccore.service.port.identity.ApplicationIdentityUseCase;
import com.municipal.rbaccore.service.port.identity.ApplicationLoginResult;
import com.municipal.rbaccore.service.port.identity.ApplicationUserResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
@ConditionalOnProperty(name = "app.identity.enabled", havingValue = "true")
public class ApplicationIdentityController {
  private final ApplicationIdentityUseCase service;

  /** 同名口令由真实处理器复制，账号协议异名显式转换。 */
  @PostMapping("/login")
  public ApplicationLoginVO login(
  @Valid
  @RequestBody
  ApplicationLoginDTO dto) {
    LoginCommand command = CloneFactory.copy(new LoginCommand(), dto);
    command.setAccount(dto.getUsername());
    ApplicationLoginResult result = service.login(command, dto.getSystems().getFirst());
    return new ApplicationLoginVO(result.accessToken(), result.tokenType(), result.expiresIn());
  }

  /** record为不可变协议对象，显式构造而不依赖处理器写入final字段。 */
  @GetMapping("/me")
  public ApplicationIdentityVO me(
      @RequestHeader(value = "Authorization", required = false)
      String header) {
    ApplicationIdentityResult result = service.current(header);
    return new ApplicationIdentityVO(
        result.subject(),
        result.roles(),
        result.permissions(),
        result.system(),
        result.permissionApis());
  }

  /** 未登记或已失效短码返回拒绝，未知短码不会隐式变为通配权限。 */
  @GetMapping("/permissions/{permission}")
  public ApplicationPermissionVO permission(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Size(max = 191)
      String permission) {
    return new ApplicationPermissionVO(service.allowed(header, permission));
  }

  /** 对外字符串身份按本项目整数主键解析，查询授权由用例处理。 */
  @GetMapping("/users/{userId}")
  public ApplicationUserVO user(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long userId) {
    ApplicationUserResult result = service.user(header, userId);
    return new ApplicationUserVO(result.userId(), result.no(), result.name());
  }
}
