package com.municipal.rbaccore.api.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本人身份保持user一层外形。 */
@Getter
@AllArgsConstructor
public class MeVO {
  private final IdentityVO user;
}
