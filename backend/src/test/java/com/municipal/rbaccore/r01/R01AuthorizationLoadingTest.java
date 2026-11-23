package com.municipal.rbaccore.r01;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.persistence.ddd.JpaAuthorizationRepositoryAdapter;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.model.policy.AuthorizationPolicy;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** R01/R08以真实隔离MySQL约束授权装载查询量，并核对完整事实和即时重读。 */
@SpringBootTest(
    classes = R01JpaConstraintTest.ModelConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.config.name=application-core-model-test")
@Transactional
class R01AuthorizationLoadingTest {
  private static final String READ_PATH = "/api/performance-fixture";

  @Autowired
  ApplicationSystemJpaRepository systems;

  @Autowired
  DepartmentJpaRepository departments;

  @Autowired
  UserJpaRepository users;

  @Autowired
  RoleJpaRepository roles;

  @Autowired
  PermissionJpaRepository permissions;

  @Autowired
  ApiResourceJpaRepository apis;

  @PersistenceContext
  EntityManager entityManager;

  /** 权限增至千项时查询按批增长，不能逐权限发SQL；停用和外系统事实仍完整保留。 */
  @ParameterizedTest
  @ValueSource(ints = {32, 1280})
  @DisplayName("R01/R08 批量装载保留完整授权事实，重读撤权立即生效且千项查询不超过40条")
  void R08_completeFactsRemainFreshWithBoundedQueries(int plannedPermissions) {
    ApplicationSystemEntity rbac =
        systems.save(new ApplicationSystemEntity("rbac", "测试权限系统"));
    List<ApplicationSystemEntity> pending = new ArrayList<>();
    for (int index = 0; index < 18; index++) {
      ApplicationSystemEntity application =
          new ApplicationSystemEntity("planned-" + index, "待接入测试系统" + index);
      application.setConnectionStatus(ConnectionStatus.PENDING);
      pending.add(systems.save(application));
    }
    DepartmentEntity root = departments.save(new DepartmentEntity("root", "测试根部门"));
    ApiResourceEntity api = apis.save(new ApiResourceEntity(rbac, "GET", READ_PATH));
    PermissionEntity allowed = new PermissionEntity(rbac, "read", "测试读取", PermissionType.BUTTON);
    allowed.mapApi(api);
    permissions.save(allowed);
    RoleEntity role = new RoleEntity("access", "测试授权角色");
    role.grantPermission(allowed);
    for (int index = 0; index < plannedPermissions; index++) {
      PermissionEntity permission =
          new PermissionEntity(
              pending.get(index % pending.size()), "planned-" + index, "测试计划权限", PermissionType.MENU);
      permission.setEnabled(index != 0);
      role.grantPermission(permissions.save(permission));
    }
    roles.save(role);
    RoleEntity empty = roles.save(new RoleEntity("empty", "测试空角色"));
    UserEntity person = new UserEntity("performance", "test-only-hash", "测试人员", root, true);
    person.assignRole(role);
    person.assignRole(empty);
    users.saveAndFlush(person);
    Long userId = person.getId();
    Long permissionId = allowed.getId();
    entityManager.clear();

    Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    boolean previouslyEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    try {
      JpaAuthorizationRepositoryAdapter repository =
          new JpaAuthorizationRepositoryAdapter(users, roles, departments);
      AuthorizationFacts facts = repository.facts(new User.Key(userId)).orElseThrow();
      long statements = statistics.getPrepareStatementCount();
      assertEquals(2, facts.user().roles().size(), "空角色仍属于完整事实");
      assertEquals(plannedPermissions + 1,
          facts.user().roles().stream().mapToInt(item -> item.permissions().size()).sum());
      assertEquals(1, facts.user().roles().stream().flatMap(item -> item.permissions().stream())
          .filter(item -> !item.enabled()).count(), "停用权限不能从事实中提前删除");
      assertEquals(Set.of(new ApiResource.Route("GET", READ_PATH)), AuthorizationPolicy.routes(facts));
      assertTrue(statements <= 40, "授权装载SQL过多：" + statements);
      System.out.printf("R08_AUTHORIZATION_LOADING permissions=%d statements=%d%n", plannedPermissions, statements);

      permissions.findById(permissionId).orElseThrow().setEnabled(false);
      permissions.flush();
      entityManager.clear();
      AuthorizationFacts revoked = repository.facts(new User.Key(userId)).orElseThrow();
      assertTrue(AuthorizationPolicy.routes(revoked).isEmpty(), "新读取必须反映数据库撤权");
      assertEquals(Set.of(new ApiResource.Route("GET", READ_PATH)), AuthorizationPolicy.routes(facts),
          "此前不可变快照不能被后一次读取改写");
    } finally {
      statistics.setStatisticsEnabled(previouslyEnabled);
    }
  }
}
