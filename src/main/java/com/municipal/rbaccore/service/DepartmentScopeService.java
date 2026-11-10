package com.municipal.rbaccore.service;

import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.importing.ImportSource;
import com.municipal.rbaccore.importing.ImportSourceRepository;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.security.CurrentIdentity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 人员归属决定本部门及后代范围，共同根身份来自T03来源映射。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentScopeService {
  private final DepartmentRepository departments;
  private final ImportSourceRepository sources;

  /** 使用实际父子外键查询范围，不按总公司字符串或角色名称放行。 */
  public List<Long> allowedIds(CurrentIdentity actor) {
    return departments.subtreeIds(actor.departmentId());
  }

  /** 全局操作必须直属经导入核实且确实覆盖全树的共同根。 */
  public boolean isGlobal(CurrentIdentity actor) {
    ImportSource source =
        sources
            .findByKindAndSourceKey(ImportSource.Kind.ORGANIZATION, "总公司")
            .orElseThrow(ApiException::scopeDenied);
    return source.getDepartment().getParent() == null
        && source.getDepartment().getId().equals(actor.departmentId())
        && departments.subtreeIds(actor.departmentId()).size() == departments.count();
  }

  /** 只有T03来源映射可指定共同根；后续写票使用同一根行作为稳定锁点。 */
  public Long trustedRootId() {
    return sources
        .findByKindAndSourceKey(ImportSource.Kind.ORGANIZATION, "总公司")
        .orElseThrow(ApiException::scopeDenied)
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
        throw new ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "部门不存在");
      }
      throw ApiException.scopeDenied();
    }
    return departments.subtreeIds(requested);
  }
}
