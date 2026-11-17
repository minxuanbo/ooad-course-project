package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R24 组织不变量在纯领域层执行，不依赖HTTP入口或数据库才能拒绝非法变更。 */
class R24DepartmentPolicyTest {
  private final Department root =
      new Department(1L, "root", "总公司", null, Department.Type.HEADQUARTERS, false);
  private final Department child =
      new Department(2L, "department", "工程部", 1L, Department.Type.DEPARTMENT, false);
  private final OrganizationFacts facts = new OrganizationFacts(1L, List.of(root, child));

  /** 可信根不能通过空节点路径删除，普通节点仍可继续核验。 */
  @Test
  @DisplayName("R24 领域规则拒绝删除可信根")
  void r24ProtectsTrustedRoot() {
    assertThrows(DomainRuleViolation.class, () -> OrganizationPolicy.requireNonRoot(root, facts));
    assertDoesNotThrow(() -> OrganizationPolicy.requireNonRoot(child, facts));
  }

  /** 任一占用事实都足以拒绝，空节点应放行。 */
  @Test
  @DisplayName("R24 领域规则拒绝有人或有下级的部门删除")
  void r24RejectsOccupiedDepartments() {
    assertThrows(DomainRuleViolation.class,
        () -> OrganizationPolicy.requireEmpty(new OrganizationFacts.Usage(true, false)));
    assertThrows(DomainRuleViolation.class,
        () -> OrganizationPolicy.requireEmpty(new OrganizationFacts.Usage(false, true)));
    assertDoesNotThrow(
        () -> OrganizationPolicy.requireEmpty(new OrganizationFacts.Usage(false, false)));
  }

  /** 新增与改名采用相同去空白规则，不误拒当前节点或不同父级同名。 */
  @Test
  @DisplayName("R24 同父名称唯一并允许原名及不同父级同名")
  void r24RejectsDuplicateNames() {
    assertThrows(DomainRuleViolation.class,
        () -> OrganizationPolicy.requireUnique(facts, 1L, " 工程部 ", null));
    assertDoesNotThrow(() -> OrganizationPolicy.requireUnique(facts, 1L, "工程部", 2L));
    assertDoesNotThrow(() -> OrganizationPolicy.requireUnique(facts, 3L, "工程部", null));
  }
}
