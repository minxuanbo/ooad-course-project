package com.municipal.rbaccore.infrastructure.logging;

import com.municipal.rbaccore.service.port.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 复用快照/末条ID/筛选绑定的Base64游标，并拒绝篡改；重启后可刷新快照。 */
@Component
public class AuditPageCursor {
  private final byte[] secret = randomKey();

  /** 游标不含令牌、口令或个人明细，筛选内容以摘要绑定。 */
  public String encode(long snapshot, long before, String fingerprint) {
    String payload = snapshot + ":" + before + ":" + fingerprint;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((payload + ":" + sign(payload)).getBytes(StandardCharsets.UTF_8));
  }

  /** 每页仍须重新鉴权；签名、筛选、上界和当前库快照均须一致。 */
  public Position decode(String cursor, String fingerprint, long currentMaximum) {
    try {
      String[] parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
      if (parts.length != 4) {
        throw new IllegalArgumentException();
      }
      long snapshot = Long.parseLong(parts[0]);
      long before = Long.parseLong(parts[1]);
      String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
      if (snapshot < 0
          || snapshot > currentMaximum
          || before < 1
          || before > snapshot
          || !parts[2].equals(fingerprint)
          || !MessageDigest.isEqual(
              sign(payload).getBytes(StandardCharsets.UTF_8),
              parts[3].getBytes(StandardCharsets.UTF_8))) {
        throw new IllegalArgumentException();
      }
      return new Position(snapshot, before);
    } catch (IllegalArgumentException exception) {
      throw BusinessException.bad("INVALID_CURSOR", "游标无效或筛选条件已改变");
    }
  }

  /** 标准HMAC保护已有分页语义，不用于登录或角色判断。 */
  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("运行环境缺少HMAC支持");
    }
  }

  /** 进程内密钥不写数据库、文件或日志。 */
  private static byte[] randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return key;
  }

  /** 已通过完整性校验的快照上界与下一页排他ID上界。 */
  public record Position(long snapshot, long before) {}
}
