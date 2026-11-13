package com.municipal.rbaccore.service.structured.security;

import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 人员级别与可授角色上限共用稳定元数据，不根据名称、职位或直属根推断管理员。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredPersonnelAuthority {
  private final UserJpaRepository users;
  private final StructuredDepartmentScopeService scope;

  /** 只有有效基础角色提高当前操作级别。 */
  public int level(UserEntity user) {
    return user.getRoles().stream()
        .filter(RoleEntity::isEnabled)
        .map(RoleEntity::getManagementLevel)
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .max()
        .orElse(1);
  }

  /** 最新人员记录作为判定来源，令牌不保存级别。 */
  public int level(CurrentIdentity actor) {
    return level(users.findById(actor.id()).orElseThrow(BusinessException::unauthorized));
  }

  /** 本人、同级、上级和自定义角色持有人由系统管理员维护。停用自定义角色仍受保护。 */
  public boolean canManage(CurrentIdentity actor, UserEntity target) {
    return canManage(snapshot(actor), target);
  }

  /** 当前查询一次计算操作人能力，避免人员每一行重复查询授权。 */
  public AuthoritySnapshot snapshot(CurrentIdentity actor) {
    int level = level(actor);
    return new AuthoritySnapshot(actor.id(), level, level == 5 && scope.isGlobal(actor));
  }

  /** 基于同一查询快照计算目标保护，实际写仍重新读取能力。 */
  public boolean canManage(AuthoritySnapshot actor, UserEntity target) {
    return actor.administrator()
        || (actor.level() >= 2
            && !actor.userId().equals(target.getId())
            && target.getRoles().stream().noneMatch(role -> role.getManagementLevel() == null)
            && level(target) < actor.level());
  }

  /** 一次查询中的安全能力快照，不存入令牌或跨请求缓存。 */
  public record AuthoritySnapshot(Long userId, int level, boolean administrator) {}

  /** 所有人员写入口与候选查询共用同级保护。 */
  public void requireManage(CurrentIdentity actor, UserEntity target) {
    if (!canManage(actor, target)) throw BusinessException.scopeDenied();
  }
}
