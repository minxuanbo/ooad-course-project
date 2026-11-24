package com.municipal.rbaccore.api.identity.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.identity.ApplicationLoginResult;

/** 老师身份客户端要求的原始登录响应，不包含权限快照。 */
@CopyFrom(ApplicationLoginResult.class)
public record ApplicationLoginVO(String accessToken, String tokenType, long expiresIn) {}
