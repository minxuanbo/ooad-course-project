package com.municipal.rbaccore.api.identity.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.identity.ApplicationUserResult;

/** 接入应用所需的最小身份投影，不输出手机号或口令。 */
@CopyFrom(ApplicationUserResult.class)
public record ApplicationUserVO(String userId, String no, String name) {}
