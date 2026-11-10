package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.Department;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 组织树查询及跨授权写入共用的稳定根锁。 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

  /** MySQL递归查询真实后代，不全量读取人员后在内存裁剪。 */
  @Query(
      value =
          "WITH RECURSIVE tree AS (SELECT id FROM departments WHERE id=:root UNION ALL SELECT d.id"
              + " FROM departments d JOIN tree t ON d.parent_id=t.id) SELECT id FROM tree",
      nativeQuery = true)
  List<Long> subtreeIds(Long root);

  /** 已核实共同根作为首次批量授权及后续管理员保护的共同锁点。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Department d where d.id=:id")
  Optional<Department> lockById(Long id);
}
