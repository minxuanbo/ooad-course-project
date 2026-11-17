package com.municipal.rbaccore.infrastructure.excel;

import com.municipal.rbaccore.service.port.error.ImportException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 离线资料准备的标识碰撞校验，按MySQL二进制规则比较，不依赖任一业务实现或请求时鉴权。 */
public final class LoginIdentifiers {
  private LoginIdentifiers() {}

  /** 同用户两个字段可以相同；不同用户的任意字段碰撞均拒绝。 */
  public static void validate(List<Identity> identities) {
    Map<String, Integer> owners = new HashMap<>();
    for (int index = 0; index < identities.size(); index++) {
      Identity identity = identities.get(index);
      claim(owners, identity.username(), index);
      if (identity.phone() != null && !identity.phone().isBlank()) {
        claim(owners, identity.phone(), index);
      }
    }
  }

  /** 只报告冲突记录位置，不在异常中打印人员标识。 */
  private static void claim(Map<String, Integer> owners, String value, int index) {
    Integer previous = owners.putIfAbsent(value.trim(), index);
    if (previous != null && previous != index) {
      throw new ImportException(
          "IDENTIFIER_CONFLICT", "登录标识冲突：第" + (previous + 1) + "条与第" + (index + 1) + "条账号记录");
    }
  }

  /** 同一人员的两个直接登录字段，不建立独立标识注册表。 */
  public record Identity(String username, String phone) {}
}
