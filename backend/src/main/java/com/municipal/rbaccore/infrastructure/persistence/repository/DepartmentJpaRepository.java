package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.identity.DepartmentDirectoryRow;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 组织树查询及跨授权写入共用的稳定根锁。 */
public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {
  /** 外部目录按同一范围和搜索条件计数，根层与直属子层不会混合。 */
  @Query(
      "select count(d) from DepartmentEntity d left join d.parent p where d.deleted=false and d.id"
          + " in :scope and ((:rootOnly=true and d.id=:selected) or (:rootOnly=false and"
          + " p.id=:selected)) and (lower(d.name) like :keyword escape '!' or lower(d.code) like"
          + " :keyword escape '!')")
  long countDirectory(boolean rootOnly, Long selected, java.util.Set<Long> scope, String keyword);

  /** 最小字段投影由Spring Data分页执行，子项计数不加载整个组织实体。 */
  @Query(
      "select new"
          + " com.municipal.rbaccore.infrastructure.persistence.identity.DepartmentDirectoryRow(d.id,d.code,d.name,p.id,(select"
          + " count(c) from DepartmentEntity c where c.parent.id=d.id and c.deleted=false)) from"
          + " DepartmentEntity d left join d.parent p where d.deleted=false and d.id in :scope and"
          + " ((:rootOnly=true and d.id=:selected) or (:rootOnly=false and p.id=:selected)) and"
          + " (lower(d.name) like :keyword escape '!' or lower(d.code) like :keyword escape '!')"
          + " order by d.id")
  java.util.List<DepartmentDirectoryRow> directory(
      boolean rootOnly,
      Long selected,
      java.util.Set<Long> scope,
      String keyword,
      Pageable pageable);

  /** 读取有效部门的当前祖先编号，UNION去重避免损坏树形成无限递归。 */
  @Query(
      value =
          "with recursive ancestors as (select id,parent_id from departments where id=:id and"
              + " deleted=false union distinct select d.id,d.parent_id from departments d join"
              + " ancestors a on d.id=a.parent_id where d.deleted=false) select id from ancestors",
      nativeQuery = true)
  java.util.Set<Long> ancestorIds(Long id);

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
