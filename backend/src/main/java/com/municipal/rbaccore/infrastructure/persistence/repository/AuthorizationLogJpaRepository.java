package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.AuthorizationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** 最小日志分页及快照均由JPA查询完成，不读取旧库历史用户引用。 */
public interface AuthorizationLogJpaRepository
    extends JpaRepository<AuthorizationLogEntity, Long>,
        JpaSpecificationExecutor<AuthorizationLogEntity> {

  /** 快照固定最大ID，新产生的技术日志不进入当前快照。 */
  @Query("select coalesce(max(log.id),0) from AuthorizationLogEntity log")
  long snapshot();
}
