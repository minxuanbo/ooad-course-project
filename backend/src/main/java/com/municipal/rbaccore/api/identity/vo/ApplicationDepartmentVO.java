package com.municipal.rbaccore.api.identity.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.identity.ApplicationDepartmentResult;

/** 统一身份部门仅用于逐层选择，不暴露人员统计或管理按钮能力。 */
@CopyFrom(ApplicationDepartmentResult.class)
public record ApplicationDepartmentVO(
    Long id, String code, String name, Long parentId, boolean hasChildren) {}
