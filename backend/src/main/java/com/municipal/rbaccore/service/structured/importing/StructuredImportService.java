package com.municipal.rbaccore.service.structured.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ImportSourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ImportSourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.importing.ImportReport;
import com.municipal.rbaccore.service.port.importing.ImportUseCase;
import com.municipal.rbaccore.service.port.importing.SourceWorkbookReader;
import com.municipal.rbaccore.service.port.security.PasswordHashing;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** T03 的离线导入事务，组织、账号及来源记录整体提交或整体回滚。 */
@RequiredArgsConstructor
public class StructuredImportService implements ImportUseCase {
  private static final ObjectMapper SOURCE_JSON = new ObjectMapper();
  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;
  private final ImportSourceJpaRepository sources;
  private final SourceWorkbookReader planner;
  private final PasswordHashing encoder;

  /** 从已验证的原表初始化新项目，重复执行不得覆盖已维护的账号和授权。 */
  @Transactional
  public ImportReport importDirectory(Path directory, BootstrapCredentials credentials) {
    if (credentials == null) {
      throw new ImportException("BOOTSTRAP_CONFIG_INVALID", "初始化口令配置缺失");
    }
    ImportPlan plan = planner.read(directory);
    List<LoginIdentifiers.Identity> plannedIdentities = new ArrayList<>();
    for (ImportPlan.Employee employee : plan.employees()) {
      plannedIdentities.add(new LoginIdentifiers.Identity(employee.username(), employee.phone()));
    }
    plannedIdentities.add(new LoginIdentifiers.Identity(credentials.getAdminUsername(), null));
    LoginIdentifiers.validate(plannedIdentities);
    try {
      if (users.countBinaryLoginColumns() != 2) {
        throw new ImportException("IMPORT_DATABASE_UNSAFE", "工号及手机号列必须采用utf8mb4_0900_bin排序规则");
      }
      List<ImportSourceEntity> existingSources = sources.findAll();
      if (!existingSources.isEmpty()) {
        return verifyExisting(plan, credentials, existingSources);
      }
      if (users.count() != 0 || departments.count() != 0) {
        throw new ImportException("TARGET_NOT_EMPTY", "目标库已有无来源映射的数据，拒绝自动合并或覆盖");
      }
      return initialize(plan, credentials);
    } catch (DataIntegrityViolationException exception) {
      throw new ImportException("DATA_CONFLICT", "导入发生数据库约束冲突，整批已回滚，可核对后重试");
    } catch (DataAccessException exception) {
      throw new ImportException("IMPORT_DATABASE_ERROR", "导入数据库操作失败，整批已回滚");
    }
  }

  /** 首次导入只建资料、账号和来源，不建立任何角色或默认授权。 */
  private ImportReport initialize(ImportPlan plan, BootstrapCredentials credentials) {
    Map<String, DepartmentEntity> targets = createDepartments(plan);
    List<ImportSourceEntity> records = new ArrayList<>();
    for (ImportPlan.Organization organization : plan.organizations()) {
      records.add(
          ImportSourceEntity.organization(
              organization,
              plan.organizationSha256(),
              organizationJson(organization),
              targets.get(organization.targetName())));
    }
    for (ImportPlan.Employee employee : plan.employees()) {
      UserEntity user =
          new UserEntity(
              employee.username(),
              encoder.encode(credentials.getEmployeePassword()),
              employee.name(),
              targets.get(employee.targetDepartment()),
              true);
      user.updateProfile(employee.phone(), employee.position());
      users.save(user);
      records.add(
          ImportSourceEntity.employee(
              employee, plan.employeeSha256(), employeeJson(employee), user));
    }
    UserEntity admin =
        users.save(
            new UserEntity(
                credentials.getAdminUsername(),
                encoder.encode(credentials.getAdminPassword()),
                "初始维护账号",
                targets.get(WorkbookImportPlanner.ROOT_NAME),
                true));
    records.add(ImportSourceEntity.bootstrap(admin));
    // 在本服务事务内捕获关联、唯一性和非空约束，统一转换后整体回滚。
    sources.saveAllAndFlush(records);
    return report(plan, targets.size(), plan.employees().size() + 1, 0, admin.getId());
  }

  /** 按依赖顺序建树，稳定编码不依赖源行顺序；领导来源不另建目标节点。 */
  private Map<String, DepartmentEntity> createDepartments(ImportPlan plan) {
    Map<String, ImportPlan.Organization> pending = new LinkedHashMap<>();
    for (ImportPlan.Organization organization : plan.organizations()) {
      if (organization.name().trim().equals(organization.targetName())) {
        pending.put(organization.targetName(), organization);
      }
    }
    Map<String, DepartmentEntity> targets = new LinkedHashMap<>();
    while (!pending.isEmpty()) {
      boolean progressed = false;
      for (String name : List.copyOf(pending.keySet())) {
        ImportPlan.Organization organization = pending.get(name);
        String parentName = organization.parent().trim();
        if (!parentName.isEmpty() && !targets.containsKey(parentName)) {
          continue;
        }
        DepartmentEntity department = new DepartmentEntity(organization.targetCode(), name);
        department.moveTo(targets.get(parentName));
        targets.put(name, departments.save(department));
        pending.remove(name);
        progressed = true;
      }
      if (!progressed) {
        throw new ImportException("ORGANIZATION_INVALID", "目标组织层级无法完整建立");
      }
    }
    return targets;
  }

  /** 原件及映射一致时只核对，不重置口令、启停、调动或后续授予的角色。 */
  private ImportReport verifyExisting(
      ImportPlan plan, BootstrapCredentials credentials, List<ImportSourceEntity> existingSources) {
    Map<String, ImportSourceEntity> indexedSources = new HashMap<>();
    for (ImportSourceEntity source : existingSources) {
      indexedSources.put(key(source.getKind(), source.getSourceKey()), source);
    }
    if (existingSources.size() != plan.organizations().size() + plan.employees().size() + 1) {
      throw new ImportException("SOURCE_MAPPING_INCOMPLETE", "来源映射数量不完整，拒绝覆盖或补猜已有账号");
    }
    Map<String, DepartmentEntity> storedDepartments = verifyStoredDepartments(plan);
    for (ImportPlan.Organization organization : plan.organizations()) {
      ImportSourceEntity source =
          requireSource(
              indexedSources, ImportSourceEntity.Kind.ORGANIZATION, organization.name().trim());
      requireSameSource(
          source, plan.organizationSha256(), organization.row(), organizationJson(organization));
      if (!source
          .getDepartment()
          .getId()
          .equals(storedDepartments.get(organization.targetName()).getId())) {
        throw new ImportException("SOURCE_CHANGED", "组织来源映射目标不一致，拒绝自动覆盖");
      }
    }
    for (ImportPlan.Employee employee : plan.employees()) {
      ImportSourceEntity source =
          requireSource(
              indexedSources, ImportSourceEntity.Kind.EMPLOYEE, employee.username().trim());
      requireSameSource(source, plan.employeeSha256(), employee.row(), employeeJson(employee));
      if (!source.getUser().getUsername().equals(employee.username().trim())) {
        throw new ImportException("SOURCE_CHANGED", "员工来源工号与目标用户不一致，拒绝自动覆盖");
      }
    }
    ImportSourceEntity admin =
        requireSource(
            indexedSources, ImportSourceEntity.Kind.BOOTSTRAP, credentials.getAdminUsername());
    if (!admin.getUser().getUsername().equals(credentials.getAdminUsername())) {
      throw new ImportException("SOURCE_CHANGED", "维护账号来源与目标用户不一致");
    }
    List<UserEntity> currentUsers = users.findAll();
    LoginIdentifiers.validate(
        currentUsers.stream()
            .map(user -> new LoginIdentifiers.Identity(user.getUsername(), user.getPhone()))
            .toList());
    return report(plan, storedDepartments.size(), 0, currentUsers.size(), admin.getUser().getId());
  }

  /** 部门结构与稳定来源一致，避免重复执行掩盖根节点、父引用或映射损坏。 */
  private Map<String, DepartmentEntity> verifyStoredDepartments(ImportPlan plan) {
    Map<String, DepartmentEntity> stored = new HashMap<>();
    for (DepartmentEntity department : departments.findAll()) {
      if (stored.putIfAbsent(department.getName(), department) != null) {
        throw new ImportException("TARGET_TREE_CHANGED", "目标组织名称不唯一");
      }
    }
    long expectedCount =
        plan.organizations().stream().map(ImportPlan.Organization::targetName).distinct().count();
    if (stored.size() != expectedCount) {
      throw new ImportException("TARGET_TREE_CHANGED", "目标组织数量与已确认的来源映射不一致");
    }
    for (ImportPlan.Organization organization : plan.organizations()) {
      if (!organization.name().trim().equals(organization.targetName())) {
        continue;
      }
      DepartmentEntity department = stored.get(organization.targetName());
      String parentName =
          department == null || department.getParent() == null
              ? ""
              : department.getParent().getName();
      if (department == null
          || !department.getCode().equals(organization.targetCode())
          || !parentName.equals(organization.parent().trim())) {
        throw new ImportException("TARGET_TREE_CHANGED", "目标组织编码或层级与来源不一致");
      }
    }
    return stored;
  }

  /** 缺失来源不能按姓名、岗位或相似字段自动认领已有目标。 */
  private static ImportSourceEntity requireSource(
      Map<String, ImportSourceEntity> indexed, ImportSourceEntity.Kind kind, String sourceKey) {
    ImportSourceEntity source = indexed.get(key(kind, sourceKey));
    if (source == null) {
      throw new ImportException("SOURCE_MAPPING_INCOMPLETE", "来源映射缺失或管理员配置与首次初始化不一致");
    }
    return source;
  }

  /** 原始内容发生变化时显式拒绝，避免将再次初始化当作资料更新接口。 */
  private static void requireSameSource(
      ImportSourceEntity source, String hash, int row, String json) {
    if (!Objects.equals(source.getFileSha256(), hash)
        || !Objects.equals(source.getSourceRow(), row)
        || !source.getSourceJson().equals(json)) {
      throw new ImportException("SOURCE_CHANGED", "原件或来源记录发生变化，需另行核对后迁移");
    }
  }

  /** 将来源类别与原始唯一键组合，避免不同类别同名造成误匹配。 */
  private static String key(ImportSourceEntity.Kind kind, String sourceKey) {
    return kind.name() + ":" + sourceKey;
  }

  /** 组织来源保留原表三列，不覆盖合并前名称与负责人。 */
  private static String organizationJson(ImportPlan.Organization organization) {
    return json(List.of(organization.name(), organization.manager(), organization.parent()));
  }

  /** 人员来源逐列保留原六列，手机号与工号始终按字符串保存。 */
  private static String employeeJson(ImportPlan.Employee employee) {
    return json(
        List.of(
            employee.name(),
            employee.username(),
            employee.company(),
            employee.department(),
            employee.position(),
            employee.phone()));
  }

  /** 序列化固定来源列，不进行DTO映射，也不把秘密加入来源JSON。 */
  private static String json(List<String> cells) {
    try {
      return SOURCE_JSON.writeValueAsString(cells);
    } catch (JsonProcessingException exception) {
      throw new ImportException("SOURCE_FIELD_INVALID", "来源字段无法保存");
    }
  }

  /** 报告只提供数量、文件摘要和维护账号ID，授权准备由T05另行验收。 */
  private static ImportReport report(
      ImportPlan plan, int targetCount, int created, int existing, Long adminId) {
    return new ImportReport(
        plan.organizations().size(),
        targetCount,
        plan.employees().size(),
        created,
        existing,
        adminId,
        plan.organizationSha256(),
        plan.employeeSha256());
  }
}
