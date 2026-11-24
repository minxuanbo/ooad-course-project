package com.municipal.rbaccore.infrastructure.onboarding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.api.onboarding.dto.OnboardingCheckDTO;
import com.municipal.rbaccore.api.onboarding.dto.OnboardingPlanDTO;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationProfileJpaRepository;
import com.municipal.rbaccore.infrastructure.web.IdentityWebConfiguration;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationOnboarding;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.onboarding.OnboardingPlan;
import java.util.*;
import org.junit.jupiter.api.*;

/** 真实解析器与技术序列化的故障边界，不用数据库mock替代业务授权。 */
class R35MetadataBoundaryTest {
  /** 导入仅认内联OpenAPI3操作；重复、外链和畸形规范明确拒绝。 */
  @Test
  @DisplayName("R35 JSON和YAML导入正常及畸形规范直接用例")
  void R35_OpenApiGrammar() {
    SafeOpenApiReader reader = new SafeOpenApiReader();
    assertEquals(
        "read",
        reader
            .read(
                "openapi: 3.1.0\n"
                    + "paths:\n"
                    + "  /items:\n"
                    + "    summary: ignored\n"
                    + "    get:\n"
                    + "      operationId: read")
            .getFirst()
            .operationId());
    List<String> invalid =
        List.of(
            "null",
            "[]",
            "{openapi: 3}",
            "{openapi: 2.0.0, paths: {}}",
            "{openapi: 3.0.0}",
            "{openapi: 3.0.0, paths: []}",
            "{openapi: 3.0.0, paths: {bad: {get: {operationId: read}}}}",
            "{openapi: 3.0.0, paths: {/a: 1}}",
            "{openapi: 3.0.0, paths: {/a: {$ref: other.yaml}}}",
            "{openapi: 3.0.0, paths: {/a: {get: 1}}}",
            "{openapi: 3.0.0, paths: {/a: {get: {}}}}",
            "{openapi: 3.0.0, paths: {/a: {get: {operationId: 1}}}}",
            "{openapi: 3.0.0, paths: {/a: {get: {operationId: bad!}}}}",
            "{openapi: 3.0.0, paths: {/a: {get: {operationId: read}, post: {operationId: read}}}}",
            "{openapi: 3.0.0, paths: {}}",
            "{openapi: 3.0.0, paths: {'/a?b': {get: {operationId: read}}}}",
            "{openapi: 3.0.0, paths: {12: {get: {operationId: read}}}}",
            "!!java.lang.String {}",
            "{openapi: 3.0.0, openapi: 3.1.0, paths: {}}");
    for (String document : invalid)
      assertEquals(
          "OPENAPI_INVALID",
          assertThrows(BusinessException.class, () -> reader.read(document), document).getCode());
    String longPath = "/" + "a".repeat(512);
    assertThrows(
        BusinessException.class,
        () ->
            reader.read(
                "{openapi: 3.0.0, paths: {'" + longPath + "': {get: {operationId: read}}}}"));
    StringBuilder many = new StringBuilder("openapi: 3.0.0\npaths:\n");
    for (int i = 0; i < 1001; i++)
      many.append("  /a")
          .append(i)
          .append(":\n    get:\n      operationId: a")
          .append(i)
          .append('\n');
    assertThrows(BusinessException.class, () -> reader.read(many.toString()));
  }

  /** 有效JSON也可能不符合条件角色结构，不能因此静默关闭全局门禁。 */
  @Test
  @DisplayName("R35 条件角色元数据损坏保守拒绝，序列化失败不返回假摘要")
  void R35_MetadataAndFingerprintFailures() throws Exception {
    OperationMetadataJson json = new OperationMetadataJson(new ObjectMapper());
    assertEquals(List.of("a", "b"), json.read(json.write(List.of("b", "a"))));
    for (String invalid :
        List.of("broken", "null", "{}", "[null]", "[\"bad role\"]", "[\"a\",\"a\"]"))
      assertEquals(
          "DATA_CONFLICT",
          assertThrows(BusinessException.class, () -> json.read(invalid)).getCode());
    ObjectMapper broken = mock(ObjectMapper.class);
    when(broken.writeValueAsString(any()))
        .thenThrow(new JsonProcessingException("isolated-serialization") {});
    assertThrows(
        IllegalStateException.class, () -> new OperationMetadataJson(broken).write(List.of("a")));
    when(broken.writeValueAsBytes(any()))
        .thenThrow(new JsonProcessingException("isolated-digest") {});
    OnboardingPlan plan =
        new OnboardingPlan("", null, null, List.of(), List.of(), List.of(), List.of());
    assertThrows(
        IllegalStateException.class,
        () -> new JsonOnboardingCodec(broken).fingerprint(plan, List.of()));
    JsonOnboardingCodec codec = new JsonOnboardingCodec(new ObjectMapper());
    assertNotEquals(
        codec.fingerprint(plan, List.of("PENDING")), codec.fingerprint(plan, List.of("CONNECTED")));
  }

  /** 入口仅提取精确Origin，默认端口归一化且不接受凭据或相对路径。 */
  @Test
  @DisplayName("R35 Origin边界不开放任意来源")
  void R35_OriginBoundaries() {
    assertEquals(
        "http://example.com",
        IdentityWebConfiguration.origin("http://EXAMPLE.COM:80/a").orElseThrow());
    assertEquals(
        "https://example.com",
        IdentityWebConfiguration.origin("HTTPS://EXAMPLE.COM:443/a").orElseThrow());
    assertEquals(
        "https://example.com:8443",
        IdentityWebConfiguration.origin("https://example.com:8443/a").orElseThrow());
    assertEquals(
        "http://example.com:8080",
        IdentityWebConfiguration.origin("http://example.com:8080/a").orElseThrow());
    for (String invalid :
        List.of(
            "/relative",
            "http:/missing-host",
            "",
            "ftp://example.com",
            "https://user:secret@example.com",
            "https://example.com/#fragment",
            "not a url")) assertTrue(IdentityWebConfiguration.origin(invalid).isEmpty(), invalid);
  }

  /** 可空字段、两类HTTP协议及默认来源空项均有独立输入边界。 */
  @Test
  @DisplayName("R35 可空凭据与入口DTO不依赖浏览器完成校验")
  void R35_InputValueBoundaries() {
    for (String account : Arrays.asList(null, "", " ", "tester"))
      for (String password : Arrays.asList(null, "", " ", "secret")) {
        boolean expected =
            (account == null || account.isBlank()) == (password == null || password.isBlank());
        assertEquals(
            expected, new OnboardingCheckDTO(null, account, password).isCredentialPairValid());
      }
    for (String url :
        Arrays.asList(
            null, " ", "http://example.com", "https://example.com", "http:/missing-host")) {
      OnboardingPlanDTO value =
          new OnboardingPlanDTO(
              "a".repeat(64), url, "LOGIN", List.of(), List.of(), List.of(), List.of());
      assertEquals(!"http:/missing-host".equals(url), value.isEntryUrlValid());
    }
    assertNotNull(
        new IdentityWebConfiguration(
                mock(ApplicationProfileJpaRepository.class),
                new String[] {"", "http://example.com"})
            .identityCors());
  }

  /** 领域对象也拒绝绕过应用服务连接停用或不完整配置。 */
  @Test
  @DisplayName("R35 接入聚合连接行为保护停用和不完整配置")
  void R35_DomainConnectionInvariants() {
    ApplicationSystem application = new ApplicationSystem(2L, "example", "示例", true);
    ApplicationOnboarding.Configuration empty =
        new ApplicationOnboarding.Configuration(
            null, "LOGIN", List.of(), List.of(), List.of(), List.of());
    ApplicationOnboarding state = new ApplicationOnboarding(application, empty, List.of());
    assertThrows(IllegalArgumentException.class, () -> state.connect(empty));
    ApplicationOnboarding disabled =
        new ApplicationOnboarding(application.revise(null, false, null), empty, List.of());
    assertThrows(IllegalArgumentException.class, () -> disabled.connect(empty));
  }
}
