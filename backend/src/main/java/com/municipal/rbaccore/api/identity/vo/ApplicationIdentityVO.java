package com.municipal.rbaccore.api.identity.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.identity.ApplicationIdentityResult;

import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Map;

/** 应用客户端当前身份，只返回实时计算后的协议短码。 */
@CopyFrom(ApplicationIdentityResult.class)
public record ApplicationIdentityVO(
    String subject,
    List<String> roles,
    List<String> permissions,
    String system,
    Map<String, List<RbacCatalog.Route>> permissionApis) {}
