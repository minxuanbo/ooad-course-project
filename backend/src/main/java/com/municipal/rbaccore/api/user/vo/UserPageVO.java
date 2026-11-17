package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.user.UserPageResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员分页与已有page、size、total契约一致。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(UserPageResult.class)
public class UserPageVO {
  @CopyFrom.Exclude(UserPageResult.class)
  private List<UserVO> items;

  private long total;
  private int page;
  private int size;
}
