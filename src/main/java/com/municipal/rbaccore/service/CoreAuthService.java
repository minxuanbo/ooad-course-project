package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbac.ApiException;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.security.BearerIdentityStore;
import com.municipal.rbaccore.security.CurrentIdentity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 复用BCrypt、LOGIN_FAILED及Bearer外形，改接新账号和统一有效管理准入。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreAuthService {
  private final UserRepository users;
  private final EffectivePermissionService permissions;
  private final PasswordEncoder encoder;
  private final BearerIdentityStore identities;

  /** 两个字段定位同一用户；歧义不选第一条，不为手机号另建口令或授权。 */
  public CoreResults.LoginResult login(String account, String password) {
    RequestAuditFilter.actor(null, null);
    List<User> matches = users.findByUsernameOrPhone(account.trim(), account.trim());
    User user = matches.size() == 1 ? matches.getFirst() : null;
    if (user == null || !user.isEnabled() || !matchesPassword(password, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "账号或密码错误");
    }
    RequestAuditFilter.actor(user.getId(), user.getUsername());
    if (!permissions.canEnter(user.getId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "MANAGEMENT_ACCESS_DENIED", "该账号没有本权限系统的有效管理权限");
    }
    return new CoreResults.LoginResult(
        identities.issue(user.getId()), identity(user), BearerIdentityStore.EXPIRES_IN);
  }

  /** 每次请求重新读取账号有效性，角色撤回不能借旧身份继续管理。 */
  public CurrentIdentity require(String header) {
    User user =
        users
            .findById(identities.require(header))
            .filter(User::isEnabled)
            .orElseThrow(ApiException::unauthorized);
    RequestAuditFilter.actor(user.getId(), user.getUsername());
    return new CurrentIdentity(user.getId(), user.getUsername(), user.getDepartment().getId());
  }

  /** 本人身份不额外要求人员查询权限。 */
  public CoreResults.IdentityResult me(String header) {
    CurrentIdentity current = require(header);
    return identity(users.findById(current.id()).orElseThrow(ApiException::unauthorized));
  }

  /** 返回本系统有效权限，不能用外业务系统权限进入管理端。 */
  public List<CoreResults.PermissionResult> myPermissions(String header) {
    CurrentIdentity current = require(header);
    return permissions.permissions(current.id()).stream().map(this::permissionResult).toList();
  }

  /** 保留既有退出端点行为，不创建新的认证流程。 */
  public void logout(String header) {
    identities.revoke(header);
  }

  /** 课程映射器复制同名字段，关系派生字段在事务内显式补充。 */
  private CoreResults.IdentityResult identity(User user) {
    CoreResults.IdentityResult result = CloneFactory.copy(new CoreResults.IdentityResult(), user);
    result.setEnabled(user.isEnabled());
    result.setDepartmentId(user.getDepartment().getId());
    return result;
  }

  /** 不把实体关系传到HTTP序列化阶段。 */
  private CoreResults.PermissionResult permissionResult(Permission permission) {
    CoreResults.PermissionResult result =
        CloneFactory.copy(new CoreResults.PermissionResult(), permission);
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
