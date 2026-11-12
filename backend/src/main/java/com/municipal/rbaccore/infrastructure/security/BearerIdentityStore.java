package com.municipal.rbaccore.infrastructure.security;

import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.IdentityTokenStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 沿用32字节随机Bearer和12小时支撑，仅将旧数据库会话改为进程内身份索引。 */
@Component
@RequiredArgsConstructor
public class BearerIdentityStore implements IdentityTokenStore {
  public static final long EXPIRES_IN = Duration.ofHours(12).toSeconds();
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Entry> identities = new ConcurrentHashMap<>();

  /** 身份有效期沿原12小时约定。 */
  public long expiresIn() {
    return EXPIRES_IN;
  }

  /** 只有账号、口令、状态及管理准入全部通过后才能调用。 */
  public String issue(Long userId) {
    Instant now = clock.instant();
    identities.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    identities.put(token, new Entry(userId, now.plusSeconds(EXPIRES_IN)));
    return token;
  }

  /** 解析现有Bearer外形，账号状态与权限仍须每次访问重新查询。 */
  public Long require(String header) {
    if (header == null || !header.startsWith("Bearer ")) {
      throw new BusinessException(401, "AUTHENTICATION_REQUIRED", "请先登录");
    }
    String token = header.substring(7);
    Entry entry = identities.get(token);
    if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
      identities.remove(token);
      throw BusinessException.unauthorized();
    }
    return entry.userId();
  }

  /** 兼容原退出行为，不新增凭证持久化或认证端点。 */
  public void revoke(String header) {
    if (header != null && header.startsWith("Bearer ")) {
      identities.remove(header.substring(7));
    }
  }

  /** 身份索引仅为运行支撑，不进入核心ER或数据库。 */
  private record Entry(Long userId, Instant expiresAt) {}
}
