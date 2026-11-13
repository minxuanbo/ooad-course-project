package com.municipal.rbaccore.r04;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 T03 单工号模型以及可选手机号的归一规则。 */
class R04UserIdentifierTest {

  /** 用户名已是工号，不应再保存另一份 employeeNo。 */
  @Test
  @DisplayName("R04 用户模型只保留 username 一份工号")
  void R04_KeepsOnlyOneEmployeeNumber() {
    assertFalse(
        Arrays.stream(UserEntity.class.getDeclaredFields())
            .map(Field::getName)
            .anyMatch("employeeNo"::equals));
  }

  /** 空白手机号不得成为可匹配的登录标识。 */
  @Test
  @DisplayName("R04 空白手机号归一为 null")
  void R04_NormalizesBlankPhone() {
    UserEntity user =
        new UserEntity("0007", "existing-hash", "样例", new DepartmentEntity("sample", "样例部门"), true);
    user.updateProfile("  ", "样例岗位");
    assertNull(user.getPhone());
    user.updateProfile(" 0012345 ", "样例岗位");
    assertEquals("0012345", user.getPhone());
    assertEquals("0007", user.getUsername());
  }
}
