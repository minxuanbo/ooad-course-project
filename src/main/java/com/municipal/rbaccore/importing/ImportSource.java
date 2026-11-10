package com.municipal.rbaccore.importing;

import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

/** 导入运行支撑记录，保留原始字段及目标ID；不作为核心ER中的业务实体。 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "import_sources", uniqueConstraints = {
    @UniqueConstraint(name = "uk_import_source_key", columnNames = {"source_type", "source_key"}),
    @UniqueConstraint(name = "uk_import_source_user", columnNames = "user_id")})
@Check(constraints = "(source_type = 'ORGANIZATION' AND department_id IS NOT NULL AND user_id IS NULL)"
    + " OR (source_type IN ('EMPLOYEE','BOOTSTRAP') AND user_id IS NOT NULL AND department_id IS NULL)")
public class ImportSource {
  /** 来源类别仅区分组织原件、员工原件和独立初始化账号。 */
  public enum Kind {
    ORGANIZATION, EMPLOYEE, BOOTSTRAP
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 16)
  private Kind kind;

  @Column(name = "source_key", nullable = false, length = 191,
      columnDefinition = "varchar(191) COLLATE utf8mb4_0900_bin")
  private String sourceKey;

  @Column(name = "file_sha256", length = 64)
  private String fileSha256;

  @Column(name = "source_row")
  private Integer sourceRow;

  @Column(name = "source_json", nullable = false, columnDefinition = "longtext")
  private String sourceJson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id", foreignKey = @ForeignKey(name = "fk_import_department"))
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_import_user"))
  private User user;

  /** 保存原组织三列；公司领导与总公司可分别追溯到同一个目标部门。 */
  static ImportSource organization(ImportPlan.Organization row, String hash, String json, Department target) {
    ImportSource source = new ImportSource();
    source.kind = Kind.ORGANIZATION;
    source.sourceKey = row.name().trim();
    source.fileSha256 = hash;
    source.sourceRow = row.row();
    source.sourceJson = json;
    source.department = target;
    return source;
  }

  /** 员工来源以唯一工号定位，原六列和原行号均不被目标归属覆盖。 */
  static ImportSource employee(ImportPlan.Employee row, String hash, String json, User target) {
    ImportSource source = new ImportSource();
    source.kind = Kind.EMPLOYEE;
    source.sourceKey = row.username().trim();
    source.fileSha256 = hash;
    source.sourceRow = row.row();
    source.sourceJson = json;
    source.user = target;
    return source;
  }

  /** 管理员只记录配置来源及用户ID，不保存初始口令或密码哈希副本。 */
  static ImportSource bootstrap(User target) {
    ImportSource source = new ImportSource();
    source.kind = Kind.BOOTSTRAP;
    source.sourceKey = target.getUsername();
    source.sourceJson = "{\"origin\":\"deployment-config\",\"version\":1}";
    source.user = target;
    return source;
  }
}
