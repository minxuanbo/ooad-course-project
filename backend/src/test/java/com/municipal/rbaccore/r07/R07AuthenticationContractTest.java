package com.municipal.rbaccore.r07;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R07按配置选择后端策略运行同一组真实HTTP契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-runtime-test")
@ActiveProfiles("core-http-test")
@Import(R07AuthenticationContractSupport.Fixtures.class)
class R07AuthenticationContractTest extends R07AuthenticationContractSupport {}
