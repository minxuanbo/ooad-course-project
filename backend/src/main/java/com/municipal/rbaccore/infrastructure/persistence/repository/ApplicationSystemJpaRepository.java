package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 系统目录按稳定编码定位，不把前端原型ID当成真实主键。 */
public interface ApplicationSystemJpaRepository
    extends JpaRepository<ApplicationSystemEntity, Long> {
  /** 本轮只选择RBAC系统，不加载Excel其他业务系统。 */
  java.util.Optional<ApplicationSystemEntity> findByCode(String code);
}
