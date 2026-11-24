package com.municipal.rbaccore.infrastructure.persistence;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** 应用查询的JPA技术谓词，不承担身份、范围或授权决策。 */
public final class ApplicationSystemSpecifications {
  private ApplicationSystemSpecifications() {}

  /** 状态和名称/编码搜索使用同一数据库谓词，通配字符按原文转义。 */
  public static Specification<ApplicationSystemEntity> matching(String q, Boolean enabled) {
    String text = q == null ? "" : q.toLowerCase(Locale.ROOT);
    String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    return (root, query, builder) ->
        builder.and(
            enabled == null ? builder.conjunction() : builder.equal(root.get("enabled"), enabled),
            builder.or(
                builder.like(builder.lower(root.get("name")), pattern, '!'),
                builder.like(builder.lower(root.get("code")), pattern, '!')));
  }
}
