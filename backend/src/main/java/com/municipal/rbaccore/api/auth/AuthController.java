package com.municipal.rbaccore.api.auth;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.auth.dto.LoginDTO;
import com.municipal.rbaccore.api.auth.vo.EffectivePermissionVO;
import com.municipal.rbaccore.api.auth.vo.IdentityVO;
import com.municipal.rbaccore.api.auth.vo.LoginVO;
import com.municipal.rbaccore.api.auth.vo.MeVO;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.auth.LoginResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 认证和本人查询沿既有路径，只做格式校验及课程DTO映射。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
  private final AuthenticationUseCase service;

  /** 工号或手机号共用一个账号输入；失败不会产生成功身份。 */
  @PostMapping("/auth/login")
  public LoginVO login(@Valid @RequestBody LoginDTO dto) {
    LoginResult result = service.login(CloneFactory.copy(new LoginCommand(), dto));
    LoginVO view = CloneFactory.copy(new LoginVO(), result);
    view.setUser(CloneFactory.copy(new IdentityVO(), result.getUser()));
    return view;
  }

  /** 保留退出的既有行为，不新增认证机制。 */
  @PostMapping("/auth/logout")
  public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
    service.logout(header);
  }

  /** 本人查询只要求有效身份，不要求人员管理权限。 */
  @GetMapping("/me")
  public MeVO me(@RequestHeader(value = "Authorization", required = false) String header) {
    return new MeVO(CloneFactory.copy(new IdentityVO(), service.me(header)));
  }

  /** 只返回本系统实际有效权限，不补导航祖先或整包业务目录。 */
  @GetMapping("/me/permissions")
  public List<EffectivePermissionVO> permissions(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.myPermissions(header).stream()
        .map(result -> CloneFactory.copy(new EffectivePermissionVO(), result))
        .toList();
  }
}
