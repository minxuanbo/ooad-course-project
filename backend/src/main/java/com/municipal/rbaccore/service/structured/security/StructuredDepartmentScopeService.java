package com.municipal.rbaccore.service.structured.security;

import com.municipal.rbaccore.infrastructure.persistence.entity.ImportSourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ImportSourceJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 人员归属决定本部门及后代范围，共同根身份来自T03来源映射。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredDepartmentScopeService {
  private final DepartmentJpaRepository departments;
  private final ImportSourceJpaRepository sources;

  /** 使用实际父子外键查询范围，不按总公司字符串或角色名称放行。 */
  public List<Long> allowedIds(CurrentIdentity actor) {
    return departments.subtreeIds(actor.departmentId());
  }

  /** 全局操作必须直属经导入核实且确实覆盖全树的共同根。 */
  public boolean isGlobal(CurrentIdentity actor) {
    ImportSourceEntity source =
        sources
            .findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司")
            .orElseThrow(BusinessException::scopeDenied);
    return source.getDepartment().getParent() == null
        && source.getDepartment().getId().equals(actor.departmentId())
        && departments.subtreeIds(actor.departmentId()).size() == departments.count();
  }

  /** 只有T03来源映射可指定共同根；后续写票使用同一根行作为稳定锁点。 */
  public Long trustedRootId() {
    return sources
        .findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司")
        .orElseThrow(BusinessException::scopeDenied)
        .getDepartment()
        .getId();
  }

  /** 范围外部门与未知部门对部门账号统一403；共同根可见范围中未知部门404。 */
  public List<Long> filteredIds(CurrentIdentity actor, Long requested) {
    List<Long> allowed = allowedIds(actor);
    if (requested == null) {
      return allowed;
    }
    if (!allowed.contains(requested)) {
      if (isGlobal(actor) && !departments.existsById(requested)) {
        throw new BusinessException(404, "RESOURCE_NOT_FOUND", "部门不存在");
      }
      throw BusinessException.scopeDenied();
    }
    return departments.subtreeIds(requested);
  }
}
