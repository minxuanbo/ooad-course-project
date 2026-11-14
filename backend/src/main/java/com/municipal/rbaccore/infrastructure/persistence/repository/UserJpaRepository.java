package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** 用户持久化；两个登录字段使用相同的二进制字符比较规则。 */
public interface UserJpaRepository
    extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

  /** 范围内启用人员聚合，不载入人员实体。 */
  long countByDepartmentIdInAndEnabledTrue(List<Long> ids);

  /** 部门删除需要同时包含启用和停用人员。 */
  boolean existsByDepartmentId(Long id);

  /** 范围内按部门聚合全部人员，不在浏览器或Service下载万人后统计。 */
  @Query(
      "select u.department.id,count(u) from UserEntity u where u.department.id in :ids group by"
          + " u.department.id")
  List<Object[]> departmentCounts(List<Long> ids);

  /** 一次读取当前页的角色摘要，避免逐人员加载角色造成N+1。 */
  @EntityGraph(attributePaths = "roles")
  @Query("select distinct u from UserEntity u where u.id in :ids")
  List<UserEntity> withRoles(List<Long> ids);

  /** 数据库完成范围、搜索、计数和分页，并在事务内加载输出所需部门。 */
  @Override
  @EntityGraph(attributePaths = "department")
  Page<UserEntity> findAll(Specification<UserEntity> specification, Pageable pageable);

  /** 首次批量授权一次加载人员、部门和已有角色，避免万人逐个查询。 */
  @EntityGraph(attributePaths = {"roles", "department"})
  @Query("select distinct u from UserEntity u")
  List<UserEntity> findAllForBootstrap();

  /** 最后管理员保护只核对已核实根下有效人员，仍按实际权限集合逐人计算。 */
  List<UserEntity> findByDepartmentIdAndEnabledTrue(Long departmentId);

  /** 一次查询保留所有匹配用户，调用方必须拒绝跨用户歧义，不取第一条。 */
  List<UserEntity> findByUsernameOrPhone(String username, String phone);

  /** JPA结构校验不覆盖排序规则，读取实际元数据防止导入判重与登录查询不一致。 */
  @Query(
      value =
          "SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE()"
              + " AND table_name='users' AND column_name IN ('username','phone')"
              + " AND collation_name='utf8mb4_0900_bin'",
      nativeQuery = true)
  long countBinaryLoginColumns();
}
