package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.ApplicationSystem;
import org.springframework.data.jpa.repository.JpaRepository;

/** 系统目录按稳定编码定位，不把前端原型ID当成真实主键。 */
public interface ApplicationSystemRepository extends JpaRepository<ApplicationSystem, Long> {
  /** 本轮只选择RBAC系统，不加载Excel其他业务系统。 */
  java.util.Optional<ApplicationSystem> findByCode(String code);
}
