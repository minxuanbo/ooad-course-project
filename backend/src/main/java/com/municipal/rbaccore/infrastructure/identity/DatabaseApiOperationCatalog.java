package com.municipal.rbaccore.infrastructure.identity;

import com.municipal.rbaccore.infrastructure.onboarding.OperationMetadataJson;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.port.identity.ApiOperationCatalog;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 运行时仅从数据库读取操作及登录元数据，撤销不回退旧文件。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatabaseApiOperationCatalog implements ApiOperationCatalog {
  private final ApiOperationJpaRepository operations;
  private final ApplicationProfileJpaRepository profiles;
  private final OperationMetadataJson json;

  /** 同一个操作名称只在JWT当前应用内定位。 */
  @Override
  public Optional<Operation> find(String system, String operationId) {
    return operations
        .findBySystemCodeAndIdOperationId(system, operationId)
        .map(
            row ->
                new Operation(
                    row.getHttpMethod(),
                    row.getPathTemplate(),
                    row.isRequiresPermission(),
                    Set.copyOf(json.read(row.getPermissionRoles()))));
  }

  /** 未采用向导的历史接入继续遵守原有有效权限准入，显式发布后使用所选登录项。 */
  @Override
  public Optional<String> loginPermission(String system) {
    return profiles.findBySystemCode(system).map(row -> row.getLoginPermissionCode());
  }
}
