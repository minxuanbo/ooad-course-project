package com.municipal.rbaccore.r07;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** R07结构化实现运行共享HTTP契约；DDD后续复用同一契约，当前不创建DDD实现。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-runtime-test")
@ActiveProfiles("core-http-test")
@Import({
  R07AuthenticationContractSupport.Fixtures.class,
  com.municipal.rbaccore.service.structured.importing.StructuredImportService.class
})
class R07StructuredAuthenticationContractTest extends R07AuthenticationContractSupport {}
