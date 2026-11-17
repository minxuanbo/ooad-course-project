package com.municipal.rbaccore.r14;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R14按配置选择后端策略运行同一组真实HTTP契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-resource-test")
@ActiveProfiles("core-resource-test")
@Import(R14ResourceContractSupport.LockFixtures.class)
class R14ResourceContractTest extends R14ResourceContractSupport {}
