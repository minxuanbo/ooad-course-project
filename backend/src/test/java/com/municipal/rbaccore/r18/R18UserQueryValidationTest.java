package com.municipal.rbaccore.r18;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.api.user.dto.UserQueryDTO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R18在API边界检查人员查询的清洗顺序与安全ID范围。 */
class R18UserQueryValidationTest {
  /** 去首尾空白先于长度校验，和其他列表使用一致的契约顺序。 */
  @Test
  @DisplayName("R10/R18 人员搜索先trim再校验有效长度")
  void R10R18_normalizesSearchBeforeValidation() {
    UserQueryDTO dto = new UserQueryDTO();
    dto.setQ("  " + "a".repeat(100) + "  ");
    assertEquals("a".repeat(100), dto.getQ());
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      assertTrue(factory.getValidator().validate(dto).isEmpty());
    }
  }

  /** 超出前后端共同安全整数范围的部门ID在请求边界拒绝。 */
  @Test
  @DisplayName("R10/R18 人员筛选部门ID不得超出正安全整数")
  void R10R18_rejectsUnsafeDepartmentId() {
    UserQueryDTO dto = new UserQueryDTO();
    dto.setDepartmentId(9007199254740992L);
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      assertFalse(factory.getValidator().validate(dto).isEmpty());
    }
  }
}
