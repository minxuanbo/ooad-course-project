package com.municipal.rbaccore.service.structured.audit;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.logging.AuditPageCursor;
import com.municipal.rbaccore.infrastructure.persistence.entity.AuthorizationLogEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.AuthorizationLogJpaRepository;
import com.municipal.rbaccore.service.port.audit.AuditLogUseCase;
import com.municipal.rbaccore.service.port.audit.LogEntryResult;
import com.municipal.rbaccore.service.port.audit.LogPageResult;
import com.municipal.rbaccore.service.port.audit.LogQuery;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
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
public class StructuredAuditLogService implements AuditLogUseCase {
  private final StructuredAuthenticationService auth;
  private final StructuredDepartmentScopeService scope;
  private final AuthorizationLogJpaRepository logs;
  private final AuditPageCursor cursors;
  private final ObjectMapper mapper;

  /** 同快照total不随翻页缩小，查询产生的新技术记录不进入旧快照。 */
  public LogPageResult list(String header, LogQuery query) {
    CurrentIdentity actor = auth.require(header);
    if (!scope.isGlobal(actor)) {
      throw BusinessException.scopeDenied();
    }
    long maximum = logs.snapshot();
    String fingerprint = fingerprint(query);
    AuditPageCursor.Position position =
        query.getCursor() == null
            ? new AuditPageCursor.Position(maximum, Long.MAX_VALUE)
            : cursors.decode(query.getCursor(), fingerprint, maximum);
    Specification<AuthorizationLogEntity> base = filter(query, position.snapshot());
    long total = logs.count(base);
    Specification<AuthorizationLogEntity> pageFilter =
        base.and((root, criteria, builder) -> builder.lessThan(root.get("id"), position.before()));
    List<AuthorizationLogEntity> page =
        logs.findAll(
                pageFilter, PageRequest.of(0, query.getLimit() + 1, Sort.by("id").descending()))
            .getContent();
    boolean more = page.size() > query.getLimit();
    List<AuthorizationLogEntity> selected =
        page.subList(0, Math.min(query.getLimit(), page.size()));
    List<LogEntryResult> items =
        selected.stream()
            .map(
                log -> {
                  LogEntryResult result = CloneFactory.copy(new LogEntryResult(), log);
                  result.setAllowed(log.isAllowed());
                  return result;
                })
            .toList();
    String next =
        more ? cursors.encode(position.snapshot(), selected.getLast().getId(), fingerprint) : null;
    return new LogPageResult(items, next, more, position.snapshot(), total);
  }

  /** 查询、快照总数与下一批均使用同一组筛选条件。 */
  private Specification<AuthorizationLogEntity> filter(LogQuery query, long snapshot) {
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
  private String fingerprint(LogQuery query) {
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
      throw new BusinessException(500, "INTERNAL_ERROR", "服务器内部错误");
    }
  }
}
