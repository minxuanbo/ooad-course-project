package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import java.util.Locale;
import org.springframework.util.Assert;

@Entity
@Table(name = "api_resources", uniqueConstraints = @UniqueConstraint(
    name = "uk_api_system_method_path", columnNames = {"system_id", "http_method", "path_template"}))
public class ApiResource {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_api_system"))
  private ApplicationSystem system;
  @Column(name = "http_method", nullable = false, length = 16)
  private String httpMethod;
  @Column(name = "path_template", nullable = false, length = 512)
  private String pathTemplate;
  @Column(nullable = false)
  private boolean enabled = true;

  protected ApiResource() {}

  public ApiResource(ApplicationSystem system, String httpMethod, String pathTemplate) {
    Assert.notNull(system, "API 必须属于一个系统");
    Assert.hasText(httpMethod, "请求方法不能为空");
    Assert.hasText(pathTemplate, "路径模板不能为空");
    this.system = system;
    this.httpMethod = httpMethod.trim().toUpperCase(Locale.ROOT);
    this.pathTemplate = pathTemplate.trim();
  }

  public Long getId() { return id; }
  public ApplicationSystem getSystem() { return system; }
  public String getHttpMethod() { return httpMethod; }
  public String getPathTemplate() { return pathTemplate; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
