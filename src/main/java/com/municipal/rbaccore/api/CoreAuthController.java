package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.LoginDTO;
import com.municipal.rbaccore.api.vo.CoreViews;
import com.municipal.rbaccore.service.CoreAuthService;
import com.municipal.rbaccore.service.CoreResults;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 认证和本人查询沿既有路径，只做格式校验及课程DTO映射。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CoreAuthController {
  private final CoreAuthService service;

  /** 工号或手机号共用一个账号输入；失败不会产生成功身份。 */
  @PostMapping("/auth/login")
  public CoreViews.LoginVO login(@Valid @RequestBody LoginDTO dto) {
    CoreResults.LoginResult result = service.login(dto.getAccount(), dto.getPassword());
    CoreViews.LoginVO view = CloneFactory.copy(new CoreViews.LoginVO(), result);
    view.setUser(CloneFactory.copy(new CoreViews.IdentityVO(), result.getUser()));
    return view;
  }

  /** 保留退出的既有行为，不新增认证机制。 */
  @PostMapping("/auth/logout")
  public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
    service.logout(header);
  }

  /** 本人查询只要求有效身份，不要求人员管理权限。 */
  @GetMapping("/me")
  public CoreViews.MeVO me(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return new CoreViews.MeVO(CloneFactory.copy(new CoreViews.IdentityVO(), service.me(header)));
  }

  /** 只返回本系统实际有效权限，不补导航祖先或整包业务目录。 */
  @GetMapping("/me/permissions")
  public List<CoreViews.PermissionVO> permissions(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.myPermissions(header).stream()
        .map(result -> CloneFactory.copy(new CoreViews.PermissionVO(), result))
        .toList();
  }
}
