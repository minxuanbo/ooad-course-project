package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.InitializationRepository;
import com.municipal.rbaccore.service.port.department.DepartmentType;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** 只保存导入和首次授权经过核验的计划，来源和回执与聚合同事务。 */
@Repository
@RequiredArgsConstructor
public class JpaInitializationRepositoryAdapter implements InitializationRepository {
  private static final ObjectMapper SOURCE_JSON = new ObjectMapper();
  private static final TypeReference<List<String>> CELLS = new TypeReference<>() {};

  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;
  private final ImportSourceJpaRepository sources;
  private final ApplicationSystemJpaRepository systems;
  private final ApiResourceJpaRepository apis;
  private final PermissionJpaRepository permissions;
  private final RoleJpaRepository roles;
  private final BootstrapReceiptJpaRepository receipts;

  /** 在一个事务中载入现有身份、来源和组织，领域核对各自关联。 */
  @Override
  public InitializationState inspect() {
    try {
      if (users.countBinaryLoginColumns() != 2) {
        return new InitializationState(false, List.of(), List.of(), List.of());
      }
      List<Department> organization =
          departments.findAll().stream().map(JpaDomainMapper::department).toList();
      List<InitializationState.Account> accounts =
          users.findAllForBootstrap().stream()
              .map(
                  row ->
                      new InitializationState.Account(
                          row.getId(),
                          row.getUsername(),
                          row.getPhone(),
                          row.getDepartment().getId(),
                          row.getDepartment().getCode(),
                          row.getRoles().size()))
              .toList();
      List<InitializationState.Source> records =
          sources.findAll().stream().map(this::readSource).toList();
      return new InitializationState(true, organization, accounts, records);
    } catch (DataAccessException exception) {
      throw new ImportException("IMPORT_DATABASE_ERROR", "读取初始化来源数据库失败");
    }
  }

  /** 按种子顺序解析组织编码及外键，整批账号与来源一并写入。 */
  @Override
  public ImportBatch.Receipt save(ImportBatch batch) {
    try {
      Map<String, DepartmentEntity> organization = new HashMap<>();
      for (ImportBatch.DepartmentSeed seed : batch.departments()) {
        DepartmentEntity row = new DepartmentEntity(seed.code(), seed.name());
        row.setType(DepartmentType.valueOf(seed.type().name()));
        row.moveTo(organization.get(seed.parentCode()));
        organization.put(seed.code(), departments.save(row));
      }
      Map<String, UserEntity> accounts = new HashMap<>();
      for (ImportBatch.AccountSeed seed : batch.accounts()) {
        UserEntity row =
            new UserEntity(
                seed.username(),
                seed.encodedPassword(),
                seed.name(),
                organization.get(seed.departmentCode()),
                true);
        row.updateProfile(seed.phone(), seed.position());
        accounts.put(seed.username(), users.save(row));
      }
      List<ImportSourceEntity> records = new ArrayList<>();
      for (ImportBatch.SourceSeed seed : batch.sources()) {
        String data = cellsJson(seed.cells());
        if (seed.kind() == InitializationState.Kind.ORGANIZATION) {
          records.add(
              ImportSourceEntity.organization(
                  seed.key(), seed.row(), seed.hash(), data, organization.get(seed.target())));
        } else {
          records.add(
              ImportSourceEntity.employee(
                  seed.key(), seed.row(), seed.hash(), data, accounts.get(seed.target())));
        }
      }
      UserEntity admin = accounts.get(batch.admin());
      records.add(ImportSourceEntity.bootstrap(admin));
      sources.saveAllAndFlush(records);
      return new ImportBatch.Receipt(organization.size(), accounts.size(), 0, admin.getId());
    } catch (DataIntegrityViolationException exception) {
      throw new ImportException("DATA_CONFLICT", "导入发生数据库约束冲突，整批已回滚，可核对后重试");
    } catch (DataAccessException exception) {
      throw new ImportException("IMPORT_DATABASE_ERROR", "导入数据库操作失败，整批已回滚");
    }
  }

  /** 回执与目录分开读取，领域判断是否允许首次发布。 */
  @Override
  public BootstrapPlan.Catalog catalog() {
    String hash =
        receipts
            .findById(BootstrapPlan.RECEIPT_KEY)
            .map(BootstrapReceiptEntity::getManifestSha256)
            .orElse(null);
    return new BootstrapPlan.Catalog(
        roles.count() == 0 && systems.count() == 0 && permissions.count() == 0 && apis.count() == 0,
        hash);
  }

  /** 按领域一次性授权计划保存目录、直接关联及回执，失败让调用事务回滚。 */
  @Override
  public void publish(BootstrapPlan.Publication plan) {
    try {
      ApplicationSystemEntity system =
          systems.save(
              new ApplicationSystemEntity(ApplicationSystem.CODE_RBAC, BootstrapPlan.SYSTEM_NAME));
      Map<String, PermissionEntity> byCode = savePermissions(system, plan.permissions());
      Map<Integer, RoleEntity> byLevel = new HashMap<>();
      for (BootstrapPlan.RoleSeed seed : plan.roles()) {
        RoleEntity row = new RoleEntity(seed.code(), seed.name());
        row.setManagementLevel(seed.level());
        seed.permissions().forEach(code -> row.grantPermission(byCode.get(code)));
        byLevel.put(seed.level(), roles.save(row));
      }
      for (UserEntity user : users.findAllForBootstrap()) {
        Integer level = plan.assignments().get(user.getId());
        if (level == null) throw new BusinessException(409, "BOOTSTRAP_CONFLICT", "首次授权账号集已变化");
        user.assignRole(byLevel.get(level));
      }
      receipts.saveAndFlush(new BootstrapReceiptEntity(plan.manifestHash(), plan.completedAt()));
    } catch (DataAccessException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "首次授权写入失败，整批已回滚");
    }
  }

  /** 使用稳定目录标识去重API，关系按计划直接持久化。 */
  private Map<String, PermissionEntity> savePermissions(
      ApplicationSystemEntity system, List<BootstrapPlan.PermissionSeed> entries) {
    PermissionEntity workspace =
        permissions.save(
            new PermissionEntity(
                system,
                BootstrapPlan.WORKSPACE_CODE,
                BootstrapPlan.WORKSPACE_NAME,
                PermissionType.MENU));
    Map<String, PermissionEntity> byCode = new HashMap<>();
    byCode.put(BootstrapPlan.WORKSPACE_CODE, workspace);
    Map<ApiResource.Route, ApiResourceEntity> byRoute = new HashMap<>();
    for (BootstrapPlan.PermissionSeed seed : entries) {
      PermissionEntity row =
          new PermissionEntity(system, seed.code(), seed.code(), PermissionType.BUTTON);
      row.moveTo(workspace);
      for (ApiResource.Route route : seed.routes()) {
        row.mapApi(
            byRoute.computeIfAbsent(
                route,
                mapping ->
                    apis.save(new ApiResourceEntity(system, mapping.method(), mapping.path()))));
      }
      byCode.put(seed.code(), permissions.save(row));
    }
    return byCode;
  }

  /** 来源文档JSON仅承载原列，不映射领域或HTTP对象。 */
  private InitializationState.Source readSource(ImportSourceEntity row) {
    InitializationState.Kind kind = InitializationState.Kind.valueOf(row.getKind().name());
    List<String> columns;
    try {
      columns =
          kind == InitializationState.Kind.BOOTSTRAP
              ? List.of()
              : SOURCE_JSON.readValue(row.getSourceJson(), CELLS);
    } catch (JsonProcessingException exception) {
      columns = List.of();
    }
    return new InitializationState.Source(
        kind,
        row.getSourceKey(),
        row.getFileSha256(),
        row.getSourceRow(),
        columns,
        row.getDepartment() == null ? null : row.getDepartment().getId(),
        row.getUser() == null ? null : row.getUser().getId());
  }

  /** 只编码拟保存的原始列，异常不输出人员内容。 */
  private String cellsJson(List<String> cells) {
    try {
      return SOURCE_JSON.writeValueAsString(cells);
    } catch (JsonProcessingException exception) {
      throw new ImportException("SOURCE_FIELD_INVALID", "来源字段无法保存");
    }
  }
}
