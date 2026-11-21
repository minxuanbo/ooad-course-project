package com.municipal.rbaccore.r29;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.security.JwtIdentityCodec;
import com.municipal.rbaccore.infrastructure.security.JwtIdentityStore;
import com.municipal.rbaccore.service.port.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R33两类登录统一签发JWT，身份格式相同且受众与撤销边界仍独立。 */
class R33JwtIdentityStoreTest {
  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String ISSUER = "https://rbac.example.test";
  private final ObjectMapper json = new ObjectMapper();

  /** 管理身份必须具有标准签名声明，不因统一格式把授权快照塞入JWT。 */
  @Test
  @DisplayName("R33管理端签发身份JWT且不包含权限快照")
  void R33_ManagementIssuesIdentityOnlyJwt() throws Exception {
    JwtIdentityCodec codec = codec();
    JwtIdentityStore store = new JwtIdentityStore(CLOCK, codec);
    String token = store.issue(7L);
    String[] parts = token.split("\\.");
    assertEquals(3, parts.length, "管理端也必须返回JWT紧凑格式");
    JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
    JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertEquals("HS256", header.get("alg").asText());
    assertEquals("JWT", header.get("typ").asText());
    assertEquals("7", claims.get("sub").asText());
    assertEquals(
        "rbac",
        claims.get("aud").isArray()
            ? claims.get("aud").get(0).asText()
            : claims.get("aud").asText());
    assertEquals(ISSUER, claims.get("iss").asText());
    assertEquals(NOW.getEpochSecond(), claims.get("iat").asLong());
    assertEquals(NOW.plusSeconds(store.expiresIn()).getEpochSecond(), claims.get("exp").asLong());
    assertFalse(claims.get("jti").asText().isBlank());
    assertFalse(claims.has("roles"));
    assertFalse(claims.has("permissions"));
    assertDoesNotThrow(() -> codec.verify(token, 7L, "rbac"));
    assertEquals(7L, store.require("Bearer " + token));
  }

  /** 相同账号的会话及应用受众相互隔离，退出只撤销指定会话。 */
  @Test
  @DisplayName("R33同格式JWT保持管理受众隔离与单会话退出")
  void R33_PreservesAudienceAndSingleSessionRevocation() {
    JwtIdentityStore store = new JwtIdentityStore(CLOCK, codec());
    String first = "Bearer " + store.issue(7L);
    String second = "Bearer " + store.issue(7L);
    String external = "Bearer " + store.issueForSystem(7L, "application-a");
    assertNotEquals(first, second);
    assertThrows(BusinessException.class, () -> store.require(external));
    assertThrows(BusinessException.class, () -> store.requireForSystem(first, "application-a"));
    store.revoke(first);
    assertThrows(BusinessException.class, () -> store.require(first));
    assertEquals(7L, store.require(second));
    assertEquals(7L, store.requireForSystem(external, "application-a"));
  }

  /** 即使签名密钥相同，新进程无原会话索引时仍拒绝旧JWT。 */
  @Test
  @DisplayName("R33管理JWT重启后失效且伪造签名不能登录")
  void R33_RequiresCurrentSessionAndUnmodifiedToken() {
    JwtIdentityCodec codec = codec();
    JwtIdentityStore first = new JwtIdentityStore(CLOCK, codec);
    JwtIdentityStore restarted = new JwtIdentityStore(CLOCK, codec);
    String token = first.issue(7L);
    assertThrows(BusinessException.class, () -> restarted.require("Bearer " + token));
    assertThrows(BusinessException.class, () -> first.require("Bearer " + token + "invalid"));
    assertEquals(7L, first.require("Bearer " + token));
  }

  /** 每个用例生成独立测试密钥，不引入可用于产品的固定默认秘密。 */
  private JwtIdentityCodec codec() {
    byte[] secret = new byte[32];
    new java.security.SecureRandom().nextBytes(secret);
    return new JwtIdentityCodec(CLOCK, Base64.getEncoder().encodeToString(secret), ISSUER);
  }
}
