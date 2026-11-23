package com.municipal.rbaccore.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** R29按真实文件加载目录，逐字段检查坏配置不会悄悄关闭权限门禁。 */
class R29OperationCatalogBoundaryTest {
  private final ObjectMapper mapper = new ObjectMapper();
  @TempDir
  Path directory;

  /** 应用和操作共同定位，空目录与无需权限均为显式配置。 */
  @Test
  @DisplayName("R29 配置目录按应用隔离且显式保存无需全局权限的操作")
  void R29_loadsExplicitOperationsAndIsolatesApplications() throws Exception {
    Map<String, Object> first = entry();
    first.put("permissionRoles", List.of("manager", "teacher"));
    Map<String, Object> second = entry();
    second.put("system", "application-b");
    second.put("requiresPermission", false);
    ConfiguredApiOperationCatalog catalog = load(Map.of("operations", List.of(first, second)));
    assertEquals("/items/{id}", catalog.find("application-a", "read-item").orElseThrow().path());
    assertTrue(catalog.find("application-a", "read-item").orElseThrow().requiresPermission());
    assertEquals(2, catalog.find("application-a", "read-item").orElseThrow().permissionRoles().size());
    assertFalse(catalog.find("application-b", "read-item").orElseThrow().requiresPermission());
    assertTrue(catalog.find("application-c", "read-item").isEmpty());
    assertTrue(new ConfiguredApiOperationCatalog(new DefaultResourceLoader(), mapper, " ")
        .find("application-a", "read-item").isEmpty());
    assertTrue(load(Map.of("operations", List.of())).find("application-a", "read-item").isEmpty());
  }

  /** 短路链每一项独立失败，不能用一个同时坏掉的输入代替所有字段。 */
  @Test
  @DisplayName("R29 任一缺失或非法操作字段都使目录加载失败")
  void R29_rejectsEachMalformedField() throws Exception {
    Map<String, List<Object>> invalid = new HashMap<>();
    invalid.put("system", java.util.Arrays.asList(null, "_invalid"));
    invalid.put("operationId", java.util.Arrays.asList(null, "invalid space"));
    invalid.put("method", java.util.Arrays.asList(null, "TRACE"));
    invalid.put("path", java.util.Arrays.asList(null, "relative", "/" + "x".repeat(512), "/items?q=1", "/items#part"));
    invalid.put("requiresPermission", java.util.Arrays.asList((Object) null));
    for (Map.Entry<String, List<Object>> field : invalid.entrySet()) {
      for (Object bad : field.getValue()) {
        Map<String, Object> input = entry();
        input.put(field.getKey(), bad);
        assertInvalid(Map.of("operations", List.of(input)));
      }
    }
    assertInvalid(Map.of());
    Map<String, Object> duplicate = entry();
    assertInvalid(Map.of("operations", List.of(duplicate, duplicate)));
    for (Object role : java.util.Arrays.asList(null, "invalid role")) {
      Map<String, Object> input = entry();
      List<Object> roles = new ArrayList<>();
      roles.add(role);
      input.put("permissionRoles", roles);
      assertInvalid(Map.of("operations", List.of(input)));
    }
  }

  /** 文件缺失和语法错误均以固定消息失败，不能退回空操作目录。 */
  @Test
  @DisplayName("R29 不可读取文件及坏JSON均失败关闭")
  void R29_rejectsUnreadableAndMalformedFiles() throws Exception {
    assertThrows(IllegalStateException.class, () -> new ConfiguredApiOperationCatalog(
        new DefaultResourceLoader(), mapper, directory.resolve("missing.json").toUri().toString()));
    Path broken = directory.resolve("broken.json");
    Files.writeString(broken, "{not json");
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new ConfiguredApiOperationCatalog(new DefaultResourceLoader(), mapper, broken.toUri().toString()));
    assertEquals("统一身份API操作配置无效，请核对接入文件", failure.getMessage());
  }

  /** 每个样例从完整有效操作变异一个字段，保留失败原因可定位性。 */
  private Map<String, Object> entry() {
    Map<String, Object> value = new HashMap<>();
    value.put("system", "application-a");
    value.put("operationId", "read-item");
    value.put("method", "GET");
    value.put("path", "/items/{id}");
    value.put("requiresPermission", true);
    return value;
  }

  /** 把配置序列化到真实临时文件，再经生产ResourceLoader读取。 */
  private ConfiguredApiOperationCatalog load(Map<String, Object> configuration) throws Exception {
    Path path = directory.resolve("operations.json");
    mapper.writeValue(path.toFile(), configuration);
    return new ConfiguredApiOperationCatalog(new DefaultResourceLoader(), mapper, path.toUri().toString());
  }

  /** 统一检查固定错误消息，避免配置值和文件内容泄露到异常。 */
  private void assertInvalid(Map<String, Object> configuration) {
    assertEquals("统一身份API操作配置无效，请核对接入文件",
        assertThrows(IllegalStateException.class, () -> load(configuration)).getMessage());
  }
}
