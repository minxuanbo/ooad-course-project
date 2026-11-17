package com.municipal.rbaccore.r11;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R11按配置选择后端策略运行同一组真实HTTP契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.name=application-core-user-test",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
@ActiveProfiles("core-user-test")
@Import(R11UserManagementContractSupport.LockFixtures.class)
class R11UserManagementContractTest extends R11UserManagementContractSupport {}
