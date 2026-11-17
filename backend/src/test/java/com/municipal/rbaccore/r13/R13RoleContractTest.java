package com.municipal.rbaccore.r13;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R13按配置选择后端策略运行同一组真实HTTP契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-role-test")
@ActiveProfiles("core-role-test")
@Import(R13RoleContractSupport.LockFixtures.class)
class R13RoleContractTest extends R13RoleContractSupport {}
