package com.municipal.rbaccore.api.identity.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.identity.ApplicationDepartmentUserResult;

/** 统一身份人员最小目录，不返回联系方式或角色。 */
@CopyFrom(ApplicationDepartmentUserResult.class)
public record ApplicationDepartmentUserVO(
    String userId, String no, String name, Long departmentId, String departmentName) {}
