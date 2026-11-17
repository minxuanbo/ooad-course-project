package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.ddd.domain.model.ImportManifest;
import com.municipal.rbaccore.service.port.importing.ImportPlan;

/** 把Excel技术读取结果显式映射为不可变领域来源。 */
public final class DddImportManifest {
  private DddImportManifest() {}

  /** 保留原行、目标稳定编码和摘要，不含凭据。 */
  public static ImportManifest from(ImportPlan source) {
    return new ImportManifest(
        source.organizations().stream()
            .map(
                row ->
                    new ImportManifest.Organization(
                        row.row(),
                        row.name(),
                        row.manager(),
                        row.parent(),
                        row.targetName(),
                        row.targetCode()))
            .toList(),
        source.employees().stream()
            .map(
                row ->
                    new ImportManifest.Person(
                        row.row(),
                        row.name(),
                        row.username(),
                        row.company(),
                        row.department(),
                        row.position(),
                        row.phone(),
                        row.targetDepartment()))
            .toList(),
        source.organizationSha256(),
        source.employeeSha256());
  }
}
