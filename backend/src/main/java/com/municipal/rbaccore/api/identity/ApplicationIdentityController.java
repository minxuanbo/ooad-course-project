package com.municipal.rbaccore.api.identity;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.identity.dto.ApplicationDirectoryQueryDTO;
import com.municipal.rbaccore.api.identity.dto.ApplicationLoginDTO;
import com.municipal.rbaccore.api.identity.vo.*;
import com.municipal.rbaccore.api.identity.vo.ApplicationIdentityVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationLoginVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationPermissionVO;
import com.municipal.rbaccore.api.identity.vo.ApplicationUserVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.api.shared.RawResponse;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.identity.ApplicationDepartmentResult;
import com.municipal.rbaccore.service.port.identity.ApplicationDepartmentUserResult;
import com.municipal.rbaccore.service.port.identity.ApplicationDirectoryPage;
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
    return CloneFactory.copy((ApplicationLoginVO) null, result);
  }

  /** 新版处理器通过规范构造器返回完整record，调用者必须接收返回值。 */
  @GetMapping("/me")
  public ApplicationIdentityVO me(
      @RequestHeader(value = "Authorization", required = false)
      String header) {
    ApplicationIdentityResult result = service.current(header);
    return CloneFactory.copy((ApplicationIdentityVO) null, result);
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
      @Size(min = 1, max = 128)
      String userId) {
    ApplicationUserResult result = service.user(header, identityId(userId));
    return CloneFactory.copy((ApplicationUserVO) null, result);
  }

  /** 仅撤销当前应用JWT，管理端令牌不能借此外部入口退出。 */
  @PostMapping("/logout")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void logout(
  @RequestHeader(value = "Authorization", required = false)
  String header) {
    service.logout(header);
  }

  /** 不传parentId返回当前可见范围根，其他情况仅展开一层。 */
  @GetMapping("/departments")
  public ApplicationDirectoryPageVO<ApplicationDepartmentVO> departments(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @RequestParam(required = false)
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long parentId,
      @Valid
      @ModelAttribute
      ApplicationDirectoryQueryDTO query) {
    ApplicationDirectoryPage<ApplicationDepartmentResult> result =
        service.departments(
            header, parentId, query.getKeyword(), query.getPage(), query.getPageSize());
    return new ApplicationDirectoryPageVO<>(
        result.items().stream()
            .map(item -> CloneFactory.copy((ApplicationDepartmentVO) null, item))
            .toList(),
        result.total(),
        result.page(),
        result.pageSize());
  }

  /** 外部人员目录只映射所需身份字段，不复用管理端人员VO。 */
  @GetMapping("/departments/{departmentId}/users")
  public ApplicationDirectoryPageVO<ApplicationDepartmentUserVO> departmentUsers(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long departmentId,
      @Valid
      @ModelAttribute
      ApplicationDirectoryQueryDTO query) {
    ApplicationDirectoryPage<ApplicationDepartmentUserResult> result =
        service.departmentUsers(
            header, departmentId, query.getKeyword(), query.getPage(), query.getPageSize());
    return new ApplicationDirectoryPageVO<>(
        result.items().stream()
            .map(item -> CloneFactory.copy((ApplicationDepartmentUserVO) null, item))
            .toList(),
        result.total(),
        result.page(),
        result.pageSize());
  }

  /** 全局门禁的false是查询结果，不能冒充业务调用被HTTP拒绝。 */
  @GetMapping("/api-permissions/{operationId}")
  public ApplicationApiAuthorityVO apiPermission(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
          @Size(min = 1, max = 128)
          @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_.-]+")
          String operationId) {
    return new ApplicationApiAuthorityVO(operationId, service.apiAllowed(header, operationId));
  }

  /** 本项目主体为正安全整数；其他合法外部字符串仍由鉴权后的用例返回不存在。 */
  private Long identityId(String value) {
    if (!value.matches("-?[0-9]+")) return null;
    try {
      long id = Long.parseLong(value);
      if (id < 1 || id > ApiLimits.MAX_ID) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException exception) {
      throw com.municipal.rbaccore.service.port.error.BusinessException.bad(
          "INVALID_ARGUMENT", "用户标识超出有效范围");
    }
  }
}
