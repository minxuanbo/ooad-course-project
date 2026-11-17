package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 组织树查询及跨授权写入共用的稳定根锁。 */
public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {

  /** 仅计算当前有效组织，来源墓碑不进入范围。 */
  long countByDeletedFalse();

  /** 空节点判断排除已删除墓碑。 */
  boolean existsByParentIdAndDeletedFalse(Long id);

  /** 同级名称判重与数据库索引保持相同的二进制比较。 */
  @Query(
      "select (count(d)>0) from DepartmentEntity d where d.deleted=false and"
          + " ((:parent is null and d.parent is null) or d.parent.id=:parent)"
          + " and trim(d.name)=:name and (:excluding is null or d.id<>:excluding)")
  boolean siblingNameExists(Long parent, String name, Long excluding);

  /** MySQL递归查询真实后代，不全量读取人员后在内存裁剪。 */
  @Query(
      value =
          "WITH RECURSIVE tree AS (SELECT id FROM departments WHERE id=:root AND deleted=0 UNION"
              + " ALL SELECT d.id FROM departments d JOIN tree t ON d.parent_id=t.id WHERE"
              + " d.deleted=0) SELECT id FROM tree",
      nativeQuery = true)
  List<Long> subtreeIds(Long root);

  /** 已核实共同根作为首次批量授权及后续管理员保护的共同锁点。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from DepartmentEntity d where d.id=:id")
  Optional<DepartmentEntity> lockById(Long id);
}
