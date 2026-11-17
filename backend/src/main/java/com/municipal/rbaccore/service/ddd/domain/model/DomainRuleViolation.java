package com.municipal.rbaccore.service.ddd.domain.model;

/** 领域规则失败不绑定HTTP状态或Spring异常。 */
public final class DomainRuleViolation extends RuntimeException {
  /** 需要映射成既有接口错误语义的规则。 */
  public enum Rule {
    ROLE_NOT_ASSIGNABLE,
    INVALID_PERMISSION,
    INVALID_MAPPING,
    INVALID_TREE,
    INVALID_CHILD
  }

  private final Rule rule;

  /** 保留稳定规则标识及可读原因。 */
  public DomainRuleViolation(Rule rule, String message) {
    super(message);
    this.rule = rule;
  }

  /** 应用适配层据规则选择既有对外业务错误。 */
  public Rule rule() {
    return rule;
  }
}
