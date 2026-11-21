package com.municipal.rbaccore.service.port.identity;

import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Map;

/** 老师协议中的主体、领域身份和当前短权限码，不作为授权缓存。 */
public record ApplicationIdentityResult(
    String subject,
    List<String> roles,
    List<String> permissions,
    String system,
    Map<String, List<RbacCatalog.Route>> permissionApis) {}
