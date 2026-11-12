package com.municipal.rbaccore.service.structured.auth;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.auth.EffectivePermissionResult;
import com.municipal.rbaccore.service.port.auth.IdentityResult;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.auth.LoginResult;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.IdentityTokenStore;
import com.municipal.rbaccore.service.port.security.PasswordHashing;
import com.municipal.rbaccore.service.port.security.RequestContext;
import com.municipal.rbaccore.service.structured.security.StructuredEffectivePermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 复用BCrypt、LOGIN_FAILED及Bearer外形，改接新账号和统一有效管理准入。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredAuthenticationService implements AuthenticationUseCase {
  private final UserJpaRepository users;
  private final StructuredEffectivePermissionService permissions;
  private final PasswordHashing encoder;
  private final IdentityTokenStore identities;
  private final RequestContext requestContext;

  /** 两个字段定位同一用户；歧义不选第一条，不为手机号另建口令或授权。 */
  public LoginResult login(LoginCommand command) {
    String account = command.getAccount();
    String password = command.getPassword();
    requestContext.actor(null, null);
    List<UserEntity> matches = users.findByUsernameOrPhone(account.trim(), account.trim());
    UserEntity user = matches.size() == 1 ? matches.getFirst() : null;
    if (user == null || !user.isEnabled() || !matchesPassword(password, user.getPasswordHash())) {
      throw new BusinessException(401, "LOGIN_FAILED", "账号或密码错误");
    }
    requestContext.actor(user.getId(), user.getUsername());
    if (!permissions.canEnter(user.getId())) {
      throw new BusinessException(403, "MANAGEMENT_ACCESS_DENIED", "该账号没有本权限系统的有效管理权限");
    }
    return new LoginResult(identities.issue(user.getId()), identity(user), identities.expiresIn());
  }

  /** 每次请求重新读取账号有效性，角色撤回不能借旧身份继续管理。 */
  public CurrentIdentity require(String header) {
    UserEntity user =
        users
            .findById(identities.require(header))
            .filter(UserEntity::isEnabled)
            .orElseThrow(BusinessException::unauthorized);
    requestContext.actor(user.getId(), user.getUsername());
    return new CurrentIdentity(user.getId(), user.getUsername(), user.getDepartment().getId());
  }

  /** 本人身份不额外要求人员查询权限。 */
  public IdentityResult me(String header) {
    CurrentIdentity current = require(header);
    return identity(users.findById(current.id()).orElseThrow(BusinessException::unauthorized));
  }

  /** 返回本系统有效权限，不能用外业务系统权限进入管理端。 */
  public List<EffectivePermissionResult> myPermissions(String header) {
    CurrentIdentity current = require(header);
    return permissions.permissions(current.id()).stream().map(this::permissionResult).toList();
  }

  /** 保留既有退出端点行为，不创建新的认证流程。 */
  public void logout(String header) {
    identities.revoke(header);
  }

  /** 课程映射器复制同名字段，关系派生字段在事务内显式补充。 */
  private IdentityResult identity(UserEntity user) {
    IdentityResult result = CloneFactory.copy(new IdentityResult(), user);
    result.setEnabled(user.isEnabled());
    result.setDepartmentId(user.getDepartment().getId());
    return result;
  }

  /** 不把实体关系传到HTTP序列化阶段。 */
  private EffectivePermissionResult permissionResult(PermissionEntity permission) {
    EffectivePermissionResult result =
        CloneFactory.copy(new EffectivePermissionResult(), permission);
    result.setSystemId(permission.getSystem().getId());
    result.setParentId(permission.getParent() == null ? null : permission.getParent().getId());
    return result;
  }

  /** 非法历史哈希和超长口令均统一失败，不泄露哈希格式异常。 */
  private boolean matchesPassword(String password, String hash) {
    try {
      return encoder.matches(password, hash);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
