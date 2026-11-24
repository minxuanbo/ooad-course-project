package com.municipal.rbaccore.service.ddd.application.onboarding;

import com.municipal.rbaccore.service.ddd.application.support.DddWrite;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationOnboarding;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import com.municipal.rbaccore.service.ddd.domain.repository.OnboardingRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.onboarding.*;
import com.municipal.rbaccore.service.port.resource.*;
import com.municipal.rbaccore.service.port.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** DDD用例编排领域接入聚合、领域仓储和技术端口，不了解JPA。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DddOnboardingService implements OnboardingUseCase {
  private final OnboardingRepository repository;
  private final ManagementAccessUseCase access;
  private final AuthenticationUseCase authentication;
  private final ManagementAuthorization authorization;
  private final DddWrite writes;
  private final OnboardingCodec codec;
  private final OpenApiReader reader;
  private final IdentityProtocolProbe probe;

  /** 当前API、可信根和有效五级资格分别检查，不接受页面传来的管理员标记。 */
  @Override
  public void checkTarget(String header, Long id) {
    access.require(header);
    if (!Integer.valueOf(5).equals(authentication.me(header).getManagementLevel()))
      throw BusinessException.scopeDenied();
    if (id != null) select(id);
  }

  /** RR快照的指纹不缓存授权，仅防止旧草稿覆盖当前配置。 */
  @Override
  public OnboardingSnapshot snapshot(String header, Long id) {
    checkTarget(header, id);
    return view(select(id));
  }

  /** 技术端口只解析文档，范围和资格由当前策略负责。 */
  @Override
  public List<OnboardingPlan.Operation> preview(String header, Long id, String document) {
    checkTarget(header, id);
    return reader.read(document);
  }

  /** 聚合校验与当前真实协议探测分开，草稿不被假定为已生效。 */
  @Override
  public OnboardingReport check(
      String header, Long id, OnboardingPlan plan, String account, String password) {
    checkTarget(header, id);
    ApplicationOnboarding current = select(id);
    match(current, plan);
    List<String> errors = current.problems(configuration(plan));
    List<OnboardingReport.Check> checks =
        List.of(new OnboardingReport.Check("TEST_ACCOUNT", "PENDING", "未填写测试账号；可在完成接入后自动复检", null));
    if (errors.isEmpty() && account != null && !account.isBlank()) {
      checks = probe.run(current.application().code(), account, password);
      if (!current.application().connected())
        checks =
            checks.stream()
                .map(
                    check ->
                        check.httpStatus() != null && check.httpStatus() == 403
                            ? new OnboardingReport.Check(
                                check.code(),
                                "PENDING",
                                "待接入应用按预期拒绝登录，完成启用后自动复检",
                                check.httpStatus())
                            : check)
                .toList();
    }
    return new OnboardingReport(errors.isEmpty(), errors, checks);
  }

  /** 获锁后重读聚合、权限和指纹，领域连接通过后由仓储保存差量。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OnboardingSnapshot complete(
      String header,
      Long id,
      OnboardingPlan plan,
      boolean businessConfirmed,
      boolean revocationConfirmed) {
    return writes.global(
        header,
        () -> {
          checkTarget(header, id);
          ApplicationOnboarding current = select(id);
          match(current, plan);
          ApplicationOnboarding.Configuration desired = configuration(plan);
          if (!businessConfirmed || !revocationConfirmed)
            throw BusinessException.bad("ONBOARDING_CONFIRMATION_REQUIRED", "请确认业务后端对象权限和专用环境撤权测试");
          if (!current.application().enabled())
            throw BusinessException.bad("ONBOARDING_INVALID", "请先恢复已移除应用");
          List<String> errors = current.problems(desired);
          if (!errors.isEmpty())
            throw BusinessException.bad("ONBOARDING_INVALID", String.join("；", errors));
          if (grantsChanged(current.configuration(), desired)
              && !authorization.allows(
                  authentication.require(header).id(), "PUT", "/api/roles/{id}/permissions"))
            throw BusinessException.forbidden();
          repository.save(current.connect(desired));
          return view(select(id));
        });
  }

  /** RBAC自身通过既有资源页维护，外部向导不改变管理端协议。 */
  private ApplicationOnboarding select(Long id) {
    ApplicationOnboarding value =
        repository
            .find(new ApplicationOnboarding.Key(id))
            .orElseThrow(BusinessException::scopeDenied);
    if (value.application().isRbac())
      throw new BusinessException(409, "ONBOARDING_RBAC_RESERVED", "RBAC自身请在资源与API中维护");
    return value;
  }

  /** 比较完整当前配置，不使用客户端自报的检查通过状态。 */
  private void match(ApplicationOnboarding current, OnboardingPlan desired) {
    if (!view(current).plan().baseFingerprint().equals(desired.baseFingerprint()))
      throw new BusinessException(409, "ONBOARDING_STALE", "配置已变化，请重新打开向导");
  }

  /** 角色权限只比较当前应用子集，其他应用不进入替换请求。 */
  private boolean grantsChanged(
      ApplicationOnboarding.Configuration current, ApplicationOnboarding.Configuration desired) {
    Map<Long, Set<String>> grants = new HashMap<>();
    for (ApplicationOnboarding.Grant grant : current.roleGrants())
      grants.put(grant.roleId(), Set.copyOf(grant.permissionCodes()));
    return desired.roleGrants().stream()
        .anyMatch(grant -> !Set.copyOf(grant.permissionCodes()).equals(grants.get(grant.roleId())));
  }

  /** 协议与领域嵌套类型不同，明确组装，不为映射工具引入领域到port依赖。 */
  private ApplicationOnboarding.Configuration configuration(OnboardingPlan plan) {
    return new ApplicationOnboarding.Configuration(
        plan.entryUrl(),
        plan.loginPermissionCode(),
        plan.apis().stream()
            .map(
                value ->
                    new ApplicationOnboarding.Api(value.method(), value.path(), value.enabled()))
            .toList(),
        plan.permissions().stream()
            .map(
                value ->
                    new ApplicationOnboarding.Permission(
                        value.code(),
                        value.name(),
                        Permission.Type.valueOf(value.type()),
                        value.parentCode(),
                        value.enabled(),
                        value.apiKeys()))
            .toList(),
        plan.operations().stream()
            .map(
                value ->
                    new ApplicationOnboarding.Operation(
                        value.operationId(),
                        value.method(),
                        value.path(),
                        value.requiresPermission(),
                        value.permissionRoles()))
            .toList(),
        plan.roleGrants().stream()
            .map(value -> new ApplicationOnboarding.Grant(value.roleId(), value.permissionCodes()))
            .toList());
  }

  /** 领域事实到共享读模型的投影，不把实体或领域集合直接交给Controller。 */
  private OnboardingSnapshot view(ApplicationOnboarding state) {
    ApplicationOnboarding.Configuration source = state.configuration();
    OnboardingPlan plan =
        new OnboardingPlan(
            "",
            source.entryUrl(),
            source.loginPermissionCode(),
            source.apis().stream()
                .map(value -> new OnboardingPlan.Api(value.method(), value.path(), value.enabled()))
                .toList(),
            source.permissions().stream()
                .map(
                    value ->
                        new OnboardingPlan.Permission(
                            value.code(),
                            value.name(),
                            value.type().name(),
                            value.parentCode(),
                            value.enabled(),
                            value.apiKeys()))
                .toList(),
            source.operations().stream()
                .map(
                    value ->
                        new OnboardingPlan.Operation(
                            value.operationId(),
                            value.method(),
                            value.path(),
                            value.requiresPermission(),
                            value.permissionRoles()))
                .toList(),
            source.roleGrants().stream()
                .map(value -> new OnboardingPlan.Grant(value.roleId(), value.permissionCodes()))
                .toList());
    SystemResult system = new SystemResult();
    system.setId(state.application().id());
    system.setCode(state.application().code());
    system.setName(state.application().name());
    system.setEnabled(state.application().enabled());
    system.setConnectionStatus(
        ConnectionStatus.valueOf(state.application().connectionStatus().name()));
    List<String> facts =
        new ArrayList<>(
            List.of(
                state.application().id().toString(),
                state.application().code(),
                state.application().name(),
                Boolean.toString(state.application().enabled()),
                state.application().connectionStatus().name()));
    for (ApplicationOnboarding.RoleOption role : state.roles())
      facts.addAll(
          List.of(
              role.id().toString(),
              role.code(),
              role.name(),
              role.type(),
              Boolean.toString(role.enabled())));
    return new OnboardingSnapshot(
        system,
        plan.withFingerprint(codec.fingerprint(plan, facts)),
        state.roles().stream()
            .map(
                value ->
                    new OnboardingSnapshot.RoleOption(
                        value.id(), value.code(), value.name(), value.type(), value.enabled()))
            .toList());
  }
}
