package com.municipal.rbaccore.r13;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R13结构化实现运行共享HTTP契约；DDD后续复用同一契约，当前不创建DDD实现。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-role-test")
@ActiveProfiles("core-role-test")
@Import(R13RoleContractSupport.LockFixtures.class)
class R13StructuredRoleContractTest extends R13RoleContractSupport {}
