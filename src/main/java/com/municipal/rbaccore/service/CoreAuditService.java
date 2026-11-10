package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.logging.AuditPageCursor;
import com.municipal.rbaccore.logging.AuthorizationLog;
import com.municipal.rbaccore.logging.AuthorizationLogRepository;
import com.municipal.rbaccore.security.CurrentIdentity;
import jakarta.persistence.criteria.Predicate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 迁移旧快照/游标查询语义到JPA，日志操作权限和根范围缺一不可。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreAuditService {
  private final CoreAuthService auth;
  private final DepartmentScopeService scope;
  private final AuthorizationLogRepository logs;
  private final AuditPageCursor cursors;
  private final ObjectMapper mapper;

  /** 同快照total不随翻页缩小，查询产生的新技术记录不进入旧快照。 */
  public LogModels.PageResult list(String header, LogModels.Search query) {
    CurrentIdentity actor = auth.require(header);
    if (!scope.isGlobal(actor)) {
      throw ApiException.scopeDenied();
    }
    long maximum = logs.snapshot();
    String fingerprint = fingerprint(query);
    AuditPageCursor.Position position =
        query.getCursor() == null
            ? new AuditPageCursor.Position(maximum, Long.MAX_VALUE)
            : cursors.decode(query.getCursor(), fingerprint, maximum);
    Specification<AuthorizationLog> base = filter(query, position.snapshot());
    long total = logs.count(base);
    Specification<AuthorizationLog> pageFilter =
        base.and((root, criteria, builder) -> builder.lessThan(root.get("id"), position.before()));
    List<AuthorizationLog> page =
        logs.findAll(
                pageFilter, PageRequest.of(0, query.getLimit() + 1, Sort.by("id").descending()))
            .getContent();
    boolean more = page.size() > query.getLimit();
    List<AuthorizationLog> selected = page.subList(0, Math.min(query.getLimit(), page.size()));
    List<LogModels.EntryResult> items =
        selected.stream()
            .map(
                log -> {
                  LogModels.EntryResult result =
                      CloneFactory.copy(new LogModels.EntryResult(), log);
                  result.setAllowed(log.isAllowed());
                  return result;
                })
            .toList();
    String next =
        more ? cursors.encode(position.snapshot(), selected.getLast().getId(), fingerprint) : null;
    return new LogModels.PageResult(items, next, more, position.snapshot(), total);
  }

  /** 查询、快照总数与下一批均使用同一组筛选条件。 */
  private Specification<AuthorizationLog> filter(LogModels.Search query, long snapshot) {
    return (root, criteria, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(builder.lessThanOrEqualTo(root.get("id"), snapshot));
      if (query.getUserId() != null)
        predicates.add(builder.equal(root.get("userId"), query.getUserId()));
      if (query.getUsername() != null)
        predicates.add(builder.equal(root.get("username"), query.getUsername()));
      if (query.getAppCode() != null)
        predicates.add(builder.equal(root.get("appCode"), query.getAppCode()));
      if (query.getAllowed() != null)
        predicates.add(builder.equal(root.get("allowed"), query.getAllowed()));
      if (query.getFrom() != null)
        predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), query.getFrom()));
      if (query.getTo() != null)
        predicates.add(builder.lessThan(root.get("occurredAt"), query.getTo()));
      if (query.getEvent() != null)
        predicates.add(builder.equal(root.get("eventType"), query.getEvent()));
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** JSON仅用于固定筛选序列的摘要，不承担对象字段映射。 */
  private String fingerprint(LogModels.Search query) {
    try {
      byte[] serialized =
          mapper.writeValueAsBytes(
              Arrays.asList(
                  query.getUserId(),
                  query.getUsername(),
                  query.getAppCode(),
                  query.getAllowed(),
                  query.getFrom(),
                  query.getTo(),
                  query.getEvent(),
                  query.getLimit()));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new ApiException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");
    }
  }
}
