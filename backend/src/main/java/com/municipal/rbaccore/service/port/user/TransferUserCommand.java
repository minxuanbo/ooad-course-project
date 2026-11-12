package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.Setter;

/** 调动目标部门为单一替换值。 */
@Getter
@Setter
public class TransferUserCommand {
  private Long departmentId;
}
