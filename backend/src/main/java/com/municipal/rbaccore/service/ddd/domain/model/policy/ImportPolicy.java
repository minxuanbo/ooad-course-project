package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 新项目导入规则和既有来源回读，领域只接收已经编码的密码。 */
public final class ImportPolicy {
  private ImportPolicy() {}

  /** 产生可核对源关系，兼容公司领导合并后共用同一目标节点。 */
  public static ImportBatch batch(
      ImportManifest plan, List<ImportBatch.AccountSeed> accounts, String admin) {
    Map<String, String> codes = targetCodes(plan);
    Map<String, ImportBatch.DepartmentSeed> ordered = new LinkedHashMap<>();
    Map<String, ImportManifest.Organization> waiting =
        plan.organizations().stream()
            .filter(row -> row.name().trim().equals(row.targetName()))
            .collect(
                Collectors.toMap(
                    ImportManifest.Organization::targetCode,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    while (!waiting.isEmpty()) {
      int before = waiting.size();
      for (ImportManifest.Organization row : List.copyOf(waiting.values())) {
        String parent = row.parent().trim();
        String parentCode = codes.get(parent);
        if (!parent.isEmpty() && !ordered.containsKey(parentCode)) continue;
        ordered.put(
            row.targetCode(),
            new ImportBatch.DepartmentSeed(
                row.targetCode(), row.targetName(), parentCode, type(row.targetName())));
        waiting.remove(row.targetCode());
      }
      if (before == waiting.size()) {
        throw new InitializationViolation("ORGANIZATION_INVALID", "目标组织层级无法完整建立");
      }
    }
    List<ImportBatch.SourceSeed> rows = new ArrayList<>();
    plan.organizations()
        .forEach(
            row ->
                rows.add(
                    new ImportBatch.SourceSeed(
                        InitializationState.Kind.ORGANIZATION,
                        row.name().trim(),
                        plan.organizationHash(),
                        row.row(),
                        row.cells(),
                        row.targetCode())));
    plan.people()
        .forEach(
            row ->
                rows.add(
                    new ImportBatch.SourceSeed(
                        InitializationState.Kind.EMPLOYEE,
                        row.username().trim(),
                        plan.peopleHash(),
                        row.row(),
                        row.cells(),
                        row.username().trim())));
    return new ImportBatch(List.copyOf(ordered.values()), accounts, rows, admin);
  }

  /** 核对原件与数据库事实，已有来源不触发任何重置或补齐。 */
  public static ImportBatch.Receipt existing(
      ImportManifest plan, InitializationState state, String admin) {
    validateDatabase(state);
    if (state.sources().isEmpty()) {
      if (!state.accounts().isEmpty() || !state.departments().isEmpty()) {
        throw new InitializationViolation("TARGET_NOT_EMPTY", "目标库已有无来源映射的数据");
      }
      return null;
    }
    Index index = inspect(plan, state);
    InitializationState.Account administrator = index.accounts().get(admin);
    InitializationState.Source binding = index.source(InitializationState.Kind.BOOTSTRAP, admin);
    if (binding == null) throw missing();
    if (administrator == null || !administrator.id().equals(binding.userId())) throw changed();
    validateIdentifiers(
        state.accounts().stream()
            .map(account -> new Identity(account.username(), account.phone()))
            .toList());
    Set<Long> sourceUsers =
        state.sources().stream()
            .filter(source -> source.kind() != InitializationState.Kind.ORGANIZATION)
            .map(InitializationState.Source::userId)
            .collect(Collectors.toSet());
    Set<Long> actualUsers =
        state.accounts().stream().map(InitializationState.Account::id).collect(Collectors.toSet());
    if (!sourceUsers.equals(actualUsers)) {
      throw missing();
    }
    return new ImportBatch.Receipt(
        targetCodes(plan).size(), 0, state.accounts().size(), administrator.id());
  }

  /** 逐行检查摘要、原始列、来源外键和稳定组织原父级，保留改名与墓碑。 */
  public static Index inspect(ImportManifest plan, InitializationState state) {
    validateDatabase(state);
    if (state.sources().size() != plan.organizations().size() + plan.people().size() + 1)
      throw missing();
    Map<String, Department> departments =
        state.departments().stream()
            .collect(Collectors.toMap(Department::code, Function.identity()));
    Map<String, InitializationState.Account> accounts =
        state.accounts().stream()
            .collect(Collectors.toMap(InitializationState.Account::username, Function.identity()));
    Map<Address, InitializationState.Source> sources =
        state.sources().stream()
            .collect(
                Collectors.toMap(
                    source -> new Address(source.kind(), source.key()), Function.identity()));
    Index index = new Index(departments, accounts, sources);
    Map<String, String> codeByTarget = targetCodes(plan);

    for (ImportManifest.Organization row : plan.organizations()) {
      Department target = departments.get(row.targetCode());
      if (target == null) throw treeChanged();
      InitializationState.Source source =
          index.source(InitializationState.Kind.ORGANIZATION, row.name().trim());
      checkSource(source, plan.organizationHash(), row.row(), row.cells());
      if (!target.id().equals(source.departmentId())) throw changed();
      if (row.name().trim().equals(row.targetName())) {
        String parentCode = codeByTarget.get(row.parent().trim());
        Department parent = parentCode == null ? null : departments.get(parentCode);
        if (!Objects.equals(parent == null ? null : parent.id(), target.parentId()))
          throw treeChanged();
      }
    }
    for (ImportManifest.Person row : plan.people()) {
      String key = row.username().trim();
      InitializationState.Source source = index.source(InitializationState.Kind.EMPLOYEE, key);
      checkSource(source, plan.peopleHash(), row.row(), row.cells());
      InitializationState.Account account = accounts.get(key);
      if (account == null || !account.id().equals(source.userId())) throw changed();
    }
    return index;
  }

  /** 每个真实账号独占其可登录标识，同账号两种标识允许一致。 */
  public static void validateIdentifiers(List<Identity> identities) {
    Map<String, Integer> owners = new HashMap<>();
    for (int index = 0; index < identities.size(); index++) {
      Identity value = identities.get(index);
      claim(owners, value.username(), index);
      if (value.phone() != null && !value.phone().isBlank()) claim(owners, value.phone(), index);
    }
  }

  /** 目标稳定编码在写库之前全部确定。 */
  public static Map<String, String> targetCodes(ImportManifest source) {
    return source.organizations().stream()
        .collect(
            Collectors.toMap(
                ImportManifest.Organization::targetName,
                ImportManifest.Organization::targetCode,
                (left, right) -> left));
  }

  /** 原件描述当前唯一根、公司、部门及项目。 */
  private static Department.Type type(String name) {
    if (ImportManifest.ROOT_NAME.equals(name)) return Department.Type.HEADQUARTERS;
    if (name.endsWith("分公司")) return Department.Type.COMPANY;
    if (name.contains("项目")) return Department.Type.PROJECT;
    return Department.Type.DEPARTMENT;
  }

  /** 标识列需要区分大小写、全半角和前后空白。 */
  private static void validateDatabase(InitializationState state) {
    if (!state.binaryIdentifiers()) {
      throw new InitializationViolation(
          "IMPORT_DATABASE_UNSAFE", "工号及手机号列必须采用utf8mb4_0900_bin排序规则");
    }
  }

  /** 源行匹配按原字段与文件摘要，而不是当前姓名或岗位。 */
  private static void checkSource(
      InitializationState.Source source, String hash, int row, List<String> cells) {
    if (source == null) throw missing();
    if (!Objects.equals(hash, source.hash())
        || !Objects.equals(row, source.row())
        || !cells.equals(source.cells())) throw changed();
  }

  /** 日志和失败消息不泄漏具体账号。 */
  private static void claim(Map<String, Integer> owners, String value, int index) {
    Integer previous = owners.putIfAbsent(value.trim(), index);
    if (previous != null && previous != index) {
      throw new InitializationViolation("IDENTIFIER_CONFLICT", "不同账号使用了相同的登录标识");
    }
  }

  /** 显式区分原件未变化但来源记录缺失。 */
  private static InitializationViolation missing() {
    return new InitializationViolation("SOURCE_MAPPING_INCOMPLETE", "来源映射缺失或管理员配置不一致");
  }

  /** 已存在来源不能被重新认领。 */
  private static InitializationViolation changed() {
    return new InitializationViolation("SOURCE_CHANGED", "原件或来源记录发生变化，需另行核对后迁移");
  }

  /** 稳定编码和父关系不能由重复导入修正。 */
  private static InitializationViolation treeChanged() {
    return new InitializationViolation("TARGET_TREE_CHANGED", "目标组织稳定编码或原有层级与来源不一致");
  }

  /** 尚未持久化的登录标识，用于交叉碰撞检查。 */
  public record Identity(String username, String phone) {}

  /** 组合键防止相同文本在不同来源类型之间碰撞。 */
  public record Address(InitializationState.Kind kind, String key) {}

  /** 已核实来源在同一次导入或授权预检中复用。 */
  public record Index(
      Map<String, Department> departments,
      Map<String, InitializationState.Account> accounts,
      Map<Address, InitializationState.Source> sources) {
    /** 使用原始来源类型和唯一键查询。 */
    public InitializationState.Source source(InitializationState.Kind kind, String key) {
      return sources.get(new Address(kind, key));
    }
  }
}
