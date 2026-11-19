package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 系统目录按稳定编码定位，不把前端原型ID当成真实主键。 */
public interface ApplicationSystemJpaRepository
    extends JpaRepository<ApplicationSystemEntity, Long> {
  /** 仅按装配层提供的显式目录读取，不枚举未接入系统。 */
  java.util.List<ApplicationSystemEntity> findByCodeInOrderByIdAsc(java.util.Set<String> codes);

  /** 本轮只选择RBAC系统，不加载Excel其他业务系统。 */
  Optional<ApplicationSystemEntity> findByCode(String code);

  /** 借用稳定目录行协调日志追加事务，不依据系统状态作业务授权判断。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select system from ApplicationSystemEntity system where system.code = :code")
  Optional<ApplicationSystemEntity> lockForAuditAppend(
  @Param("code")
  String code);
}
