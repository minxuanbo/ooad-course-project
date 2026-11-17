package com.municipal.rbaccore.service.ddd.application.importing;

import com.municipal.rbaccore.service.ddd.application.support.DddImportManifest;
import com.municipal.rbaccore.service.ddd.domain.model.ImportBatch;
import com.municipal.rbaccore.service.ddd.domain.model.ImportManifest;
import com.municipal.rbaccore.service.ddd.domain.model.InitializationViolation;
import com.municipal.rbaccore.service.ddd.domain.model.policy.ImportPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.InitializationRepository;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.*;
import com.municipal.rbaccore.service.port.security.PasswordHashing;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 离线岗位与账号导入，整个计划按领域规则核验后一次事务提交。 */
@Service
@RequiredArgsConstructor
public class DddImportService implements ImportUseCase {
  private final SourceWorkbookReader source;
  private final InitializationRepository initialization;
  private final PasswordHashing passwords;

  /** 已存在的来源只验证，新的部署为每人独立编码口令并保存。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ImportReport importDirectory(Path directory, BootstrapCredentials credentials) {
    if (credentials == null) throw new ImportException("BOOTSTRAP_CONFIG_INVALID", "初始化口令配置缺失");
    ImportManifest manifest = DddImportManifest.from(source.read(directory));
    String admin = credentials.getAdminUsername();
    try {
      List<ImportPolicy.Identity> identifiers = new ArrayList<>();
      manifest
          .people()
          .forEach(row -> identifiers.add(new ImportPolicy.Identity(row.username(), row.phone())));
      identifiers.add(new ImportPolicy.Identity(admin, null));
      ImportPolicy.validateIdentifiers(identifiers);
      ImportBatch.Receipt receipt =
          ImportPolicy.existing(manifest, initialization.inspect(), admin);
      if (receipt == null) {
        receipt =
            initialization.save(
                ImportPolicy.batch(manifest, accounts(manifest, credentials), admin));
      }
      return new ImportReport(
          manifest.organizations().size(),
          receipt.departments(),
          manifest.people().size(),
          receipt.created(),
          receipt.existing(),
          receipt.adminId(),
          manifest.organizationHash(),
          manifest.peopleHash());
    } catch (InitializationViolation exception) {
      throw new ImportException(exception.code(), exception.getMessage());
    }
  }

  /** 密码只在应用层交给哈希端口，领域对象从未看到明文。 */
  private List<ImportBatch.AccountSeed> accounts(
      ImportManifest manifest, BootstrapCredentials credentials) {
    Map<String, String> codes = ImportPolicy.targetCodes(manifest);
    List<ImportBatch.AccountSeed> result = new ArrayList<>();
    for (ImportManifest.Person row : manifest.people()) {
      result.add(
          new ImportBatch.AccountSeed(
              row.username().trim(),
              passwords.encode(credentials.getEmployeePassword()),
              row.name(),
              row.phone(),
              row.position(),
              codes.get(row.target())));
    }
    result.add(
        new ImportBatch.AccountSeed(
            credentials.getAdminUsername(),
            passwords.encode(credentials.getAdminPassword()),
            "初始维护账号",
            null,
            null,
            codes.get(ImportManifest.ROOT_NAME)));
    return result;
  }
}
