package com.municipal.rbaccore.r11;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R11结构化实现运行共享HTTP契约；DDD后续复用同一契约，当前不创建DDD实现。 */
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
class R11StructuredUserManagementContractTest extends R11UserManagementContractSupport {}
