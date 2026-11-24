package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 逐请求读取应用登录资格配置，不缓存接入状态。 */
public interface ApplicationProfileJpaRepository
    extends JpaRepository<ApplicationProfileEntity, Long> {
  /** CORS只读取已启用且已接入应用的显式入口，不加载权限或人员。 */
  @Query(
      "select p.entryUrl from ApplicationProfileEntity p where p.system.enabled=true and"
          + " p.system.connectionStatus=ConnectionStatus.CONNECTED and p.entryUrl is not null")
  java.util.List<String> activeEntryUrls();

  /** 只匹配数据库登记的同一应用。 */
  Optional<ApplicationProfileEntity> findBySystemCode(String code);
}
