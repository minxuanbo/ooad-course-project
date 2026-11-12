package com.municipal.rbaccore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import org.springframework.util.Assert;

/** 部门树持久化记录，局部树约束防止自指和循环；人员管理范围由Service计算。 */
@Entity
@Table(
    name = "departments",
    uniqueConstraints = @UniqueConstraint(name = "uk_department_code", columnNames = "code"))
@cn.edu.xmu.clonefactory.CopyTo({com.municipal.rbaccore.service.port.user.DepartmentResult.class})
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DepartmentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 191)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_department_parent"))
  private DepartmentEntity parent;

  /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
  public DepartmentEntity(String code, String name) {
    Assert.hasText(code, "部门编码不能为空");
    Assert.hasText(name, "部门名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  /** 变更直接上级前检查自指和循环，失败时保留原有树关系。 */
  public void moveTo(DepartmentEntity parent) {
    Set<Object> visited = new HashSet<>();
    for (DepartmentEntity node = parent; node != null; node = node.getParent()) {
      Object key = node.getId() == null ? node : node.getId();
      Assert.isTrue(
          node != this && (id == null || !id.equals(node.getId())) && visited.add(key),
          "部门不能自指或形成循环");
    }
    this.parent = parent;
  }
}
