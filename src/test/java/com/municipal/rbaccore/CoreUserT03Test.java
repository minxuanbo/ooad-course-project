package com.municipal.rbaccore;

import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.User;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 T03 单工号模型以及可选手机号的归一规则。 */
class CoreUserT03Test {

  /** 用户名已是工号，不应再保存另一份 employeeNo。 */
  @Test
  @DisplayName("T03 用户模型只保留 username 一份工号")
  void t03KeepsOnlyOneEmployeeNumber() {
    assertFalse(Arrays.stream(User.class.getDeclaredFields())
        .map(Field::getName).anyMatch("employeeNo"::equals));
  }

  /** 空白手机号不得成为可匹配的登录标识。 */
  @Test
  @DisplayName("T03 空白手机号归一为 null")
  void t03NormalizesBlankPhone() {
    User user = new User("0007", "existing-hash", "样例",
        new Department("sample", "样例部门"), true);
    user.updateProfile("  ", "样例岗位");
    assertNull(user.getPhone());
    user.updateProfile(" 0012345 ", "样例岗位");
    assertEquals("0012345", user.getPhone());
    assertEquals("0007", user.getUsername());
  }
}
