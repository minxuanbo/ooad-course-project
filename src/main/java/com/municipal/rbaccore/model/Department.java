package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import org.springframework.util.Assert;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(name = "uk_department_code", columnNames = "code"))
public class Department {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, length = 100)
  private String code;
  @Column(nullable = false, length = 191)
  private String name;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_department_parent"))
  private Department parent;

  protected Department() {}

  public Department(String code, String name) {
    Assert.hasText(code, "部门编码不能为空");
    Assert.hasText(name, "部门名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  public void moveTo(Department parent) {
    Set<Object> visited = new HashSet<>();
    for (Department node = parent; node != null; node = node.getParent()) {
      Object key = node.getId() == null ? node : node.getId();
      Assert.isTrue(node != this && (id == null || !id.equals(node.getId())) && visited.add(key),
          "部门不能自指或形成循环");
    }
    this.parent = parent;
  }

  public Long getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public Department getParent() { return parent; }
}
