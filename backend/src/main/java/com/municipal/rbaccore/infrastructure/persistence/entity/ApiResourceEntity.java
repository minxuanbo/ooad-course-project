package com.municipal.rbaccore.infrastructure.persistence.entity;


import jakarta.persistence.*;
import java.util.Locale;
import org.springframework.util.Assert;

/** API持久化记录，规范化方法/模板并归属单一系统；业务鉴权由结构化Service执行。 */
@Entity
@Table(
    name = "api_resources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_api_system_method_path",
            columnNames = {"system_id", "http_method", "path_template"}))
@cn.edu.xmu.clonefactory.CopyTo({com.municipal.rbaccore.service.port.resource.ApiResult.class})
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@cn.edu.xmu.clonefactory.CopyNotNullFrom(
    com.municipal.rbaccore.service.port.resource.UpdateApiCommand.class)
public class ApiResourceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "system_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_api_system"))
  private ApplicationSystemEntity system;

  @Column(name = "http_method", nullable = false, length = 16)
  private String httpMethod;

  @Column(name = "path_template", nullable = false, length = 512)
  private String pathTemplate;

  @Column(nullable = false)
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({
    com.municipal.rbaccore.service.port.resource.ApiResult.class
  })
  @lombok.Setter
  private boolean enabled = true;

  /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
  public ApiResourceEntity(ApplicationSystemEntity system, String httpMethod, String pathTemplate) {
    Assert.notNull(system, "API 必须属于一个系统");
    Assert.hasText(httpMethod, "请求方法不能为空");
    Assert.hasText(pathTemplate, "路径模板不能为空");
    this.system = system;
    this.httpMethod = httpMethod.trim().toUpperCase(Locale.ROOT);
    this.pathTemplate = pathTemplate.trim();
  }
}
