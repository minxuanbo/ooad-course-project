package com.municipal.rbaccore.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记实际管理入口，权限由服务端方法/路径与JPA映射决定，不在注解中写角色。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CoreManaged {}
