package com.municipal.rbaccore.infrastructure.security;

import com.municipal.rbaccore.service.port.error.BusinessException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

/** 标准JWT编解码只承担身份完整性，不将角色或权限装入令牌。 */
@Component
public class JwtIdentityCodec {
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final String issuer;
  private final Clock clock;

  /** 显式秘密须至少256位；未提供时生成本进程随机秘密，重启后旧会话失效。 */
  public JwtIdentityCodec(
      Clock clock,
      @Value("${app.identity.jwt-secret:}")
      String encodedSecret,
      @Value("${app.identity.issuer:http://127.0.0.1:8081}")
      String issuer) {
    byte[] secret;
    try {
      if (encodedSecret.isBlank()) {
        secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
      } else {
        secret = Base64.getDecoder().decode(encodedSecret);
      }
      if (secret.length < 32 || issuer.isBlank()) throw new IllegalArgumentException();
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("JWT身份配置无效，秘密须为至少256位的Base64值");
    }
    this.issuer = issuer;
    this.clock = clock;
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    NimbusJwtDecoder verifier =
        NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    MappedJwtClaimSetConverter claimsConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
    // 标准转换器会补上缺失的iat，身份协议必须校验签名载荷实际提供的声明。
    verifier.setClaimSetConverter(
        claims -> {
          Map<String, Object> converted = claimsConverter.convert(claims);
          if (!claims.containsKey("iat")) converted.remove("iat");
          return converted;
        });
    JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
    timestamp.setClock(clock);
    verifier.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer)));
    decoder = verifier;
  }

  /** JWT只含sub、aud、iss、iat、exp及唯一jti，生命周期由身份存储决定。 */
  public String encode(Long userId, String system, Instant now, Instant expiresAt, String id) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .audience(List.of(system))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(id)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** 限定签名算法、签发者、有效期、主体和单一受众，不能把客户端自报字段当身份。 */
  public void verify(String token, Long userId, String system) {
    try {
      Jwt decoded = decoder.decode(token);
      if (!userId.toString().equals(decoded.getSubject())
          || !List.of(system).equals(decoded.getAudience())
          || decoded.getId() == null
          || decoded.getIssuedAt() == null
          || decoded.getExpiresAt() == null
          || !decoded.getExpiresAt().isAfter(clock.instant())) {
        throw BusinessException.unauthorized();
      }
    } catch (JwtException | IllegalArgumentException exception) {
      throw BusinessException.unauthorized();
    }
  }
}
