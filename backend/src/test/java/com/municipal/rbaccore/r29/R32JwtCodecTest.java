package com.municipal.rbaccore.r29;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.security.JwtIdentityCodec;
import com.municipal.rbaccore.service.port.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R32直接验证标准签名与时钟边界，避免仅凭会话索引证明JWT正确。 */
class R32JwtCodecTest {
  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  /** 正常令牌校验通过，错误主体或受众均拒绝。 */
  @Test
  @DisplayName("R32 JWT签名正确仍须核对主体及受众")
  void R32_VerifiesIdentityAndAudience() {
    JwtIdentityCodec codec = new JwtIdentityCodec(CLOCK, secret(), "https://rbac.example.test");
    String token = codec.encode(7L, "application-a", NOW, NOW.plusSeconds(60), "test-id");
    assertDoesNotThrow(() -> codec.verify(token, 7L, "application-a"));
    assertThrows(BusinessException.class, () -> codec.verify(token, 8L, "application-a"));
    assertThrows(BusinessException.class, () -> codec.verify(token, 7L, "application-b"));
  }

  /** 同一秘密下的过期边界和不同签发者不可混用。 */
  @Test
  @DisplayName("R32 JWT到期立即失效且签发者必须一致")
  void R32_RejectsExpiryAndIssuer() {
    String key = secret();
    JwtIdentityCodec codec = new JwtIdentityCodec(CLOCK, key, "https://rbac.example.test");
    String expired = codec.encode(7L, "application-a", NOW.minusSeconds(60), NOW, "expired");
    assertThrows(BusinessException.class, () -> codec.verify(expired, 7L, "application-a"));
    JwtIdentityCodec other = new JwtIdentityCodec(CLOCK, key, "https://other.example.test");
    String foreign = other.encode(7L, "application-a", NOW, NOW.plusSeconds(60), "other");
    assertThrows(BusinessException.class, () -> codec.verify(foreign, 7L, "application-a"));
  }

  /** 签名必须来自本服务秘密，篡改后的紧凑格式也拒绝。 */
  @Test
  @DisplayName("R32 JWT拒绝其他密钥及篡改签名")
  void R32_RejectsForeignSignature() {
    JwtIdentityCodec codec = new JwtIdentityCodec(CLOCK, secret(), "https://rbac.example.test");
    JwtIdentityCodec other = new JwtIdentityCodec(CLOCK, secret(), "https://rbac.example.test");
    String foreign = other.encode(7L, "application-a", NOW, NOW.plusSeconds(60), "foreign");
    assertThrows(BusinessException.class, () -> codec.verify(foreign, 7L, "application-a"));
    String[] parts = foreign.split("\\.");
    assertThrows(
        BusinessException.class,
        () -> codec.verify(parts[0] + "." + parts[1] + ".invalid", 7L, "application-a"));
  }

  /** 错误秘密只返回固定配置错误，不回显秘密原文。 */
  @Test
  @DisplayName("R32 JWT拒绝短密钥及非法编码")
  void R32_RejectsInvalidConfiguration() {
    assertThrows(
        IllegalStateException.class, () -> new JwtIdentityCodec(CLOCK, "invalid!", "issuer"));
    assertThrows(IllegalStateException.class, () -> new JwtIdentityCodec(CLOCK, "YQ==", "issuer"));
    assertThrows(IllegalStateException.class, () -> new JwtIdentityCodec(CLOCK, secret(), " "));
  }

  /** 即使使用正确签名，省略必需身份声明的令牌也不能接受。 */
  @Test
  @DisplayName("R32 正确签名但缺少jti或时间声明的JWT仍拒绝")
  void R32_RejectsMissingRequiredClaims() throws Exception {
    String key = secret();
    JwtIdentityCodec codec = new JwtIdentityCodec(CLOCK, key, "https://rbac.example.test");
    for (String missing : java.util.List.of("jti", "iat", "exp")) {
      com.nimbusds.jwt.JWTClaimsSet.Builder builder = new com.nimbusds.jwt.JWTClaimsSet.Builder()
              .issuer("https://rbac.example.test").subject("7").audience(java.util.List.of("application-a"));
      if (!missing.equals("jti")) builder.jwtID("test-id");
      if (!missing.equals("iat")) builder.issueTime(java.util.Date.from(NOW));
      if (!missing.equals("exp")) builder.expirationTime(java.util.Date.from(NOW.plusSeconds(60)));
      com.nimbusds.jwt.SignedJWT signed = new com.nimbusds.jwt.SignedJWT(
          new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), builder.build());
      signed.sign(new com.nimbusds.jose.crypto.MACSigner(Base64.getDecoder().decode(key)));
      String token = signed.serialize();
      assertFalse(signed.getJWTClaimsSet().getClaims().containsKey(missing));
      assertEquals(401, assertThrows(BusinessException.class,
          () -> codec.verify(token, 7L, "application-a"), missing).getStatus());
    }
  }

  /** 测试秘密每次独立生成，不给产品引入测试默认密钥。 */
  private String secret() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
