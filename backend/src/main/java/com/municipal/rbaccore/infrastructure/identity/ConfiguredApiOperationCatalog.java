package com.municipal.rbaccore.infrastructure.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.service.port.identity.ApiOperationCatalog;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** 从显式接入文件读取操作元数据，不维护业务系统代码白名单或课程权限逻辑。 */
@Component
public class ConfiguredApiOperationCatalog implements ApiOperationCatalog {
  private final Map<Key, Operation> operations;

  /** 无文件时没有已登记操作；损坏或重复配置使启动失败而不是静默放行。 */
  public ConfiguredApiOperationCatalog(
      ResourceLoader resources,
      ObjectMapper mapper,
      @Value("${app.identity.operations-file:}")
      String location) {
    Map<Key, Operation> indexed = new HashMap<>();
    if (!location.isBlank()) {
      try (java.io.InputStream input = resources.getResource(location).getInputStream()) {
        Configuration configuration = mapper.readValue(input, Configuration.class);
        if (configuration.operations() == null) throw new IllegalArgumentException();
        for (Entry entry : configuration.operations()) {
          if (entry.system() == null
              || !entry.system().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")
              || entry.operationId() == null
              || !entry.operationId().matches("[A-Za-z0-9_.-]{1,128}")
              || entry.method() == null
              || !entry.method().matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
              || entry.path() == null
              || !entry.path().startsWith("/")
              || entry.path().length() > 512
              || entry.path().contains("?")
              || entry.path().contains("#")
              || entry.requiresPermission() == null) throw new IllegalArgumentException();
          Key key = new Key(entry.system(), entry.operationId());
          Set<String> roles = entry.permissionRoles() == null ? Set.of() : entry.permissionRoles();
          if (roles.stream()
              .anyMatch(role -> role == null || !role.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))) {
            throw new IllegalArgumentException();
          }
          Operation operation =
              new Operation(entry.method(), entry.path(), entry.requiresPermission(), roles);
          if (indexed.putIfAbsent(key, operation) != null) throw new IllegalArgumentException();
        }
      } catch (IOException | IllegalArgumentException exception) {
        throw new IllegalStateException("统一身份API操作配置无效，请核对接入文件");
      }
    }
    operations = Map.copyOf(indexed);
  }

  /** 相同operationId可在不同应用登记，不跨应用退回匹配。 */
  @Override
  public Optional<Operation> find(String system, String operationId) {
    return Optional.ofNullable(operations.get(new Key(system, operationId)));
  }

  /** 应用与操作编号共同组成稳定键。 */
  private record Key(String system, String operationId) {}

  /** 配置只表达元数据，不保存运行期授权结论。 */
  private record Configuration(List<Entry> operations) {}

  /** 显式布尔值避免遗漏配置意外变成无需全局权限。 */
  private record Entry(
      String system,
      String operationId,
      String method,
      String path,
      Boolean requiresPermission,
      Set<String> permissionRoles) {}
}
