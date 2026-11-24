package com.municipal.rbaccore.infrastructure.onboarding;

import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.onboarding.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** 将JSON/YAML规范转为预览，不执行自定义类型、不联网解析引用。 */
@Component
public class SafeOpenApiReader implements OpenApiReader {
  /** 重复键、别名、过深文档及外部路径引用均保守拒绝。 */
  @Override
  public List<OnboardingPlan.Operation> read(String document) {
    try {
      LoaderOptions options = new LoaderOptions();
      options.setAllowDuplicateKeys(false);
      options.setMaxAliasesForCollections(0);
      options.setNestingDepthLimit(50);
      options.setCodePointLimit(1048576);
      Object loaded = new Yaml(new SafeConstructor(options)).load(document);
      if (!(loaded instanceof Map<?, ?> root)
          || !(root.get("openapi") instanceof String version)
          || !version.matches("3[.][01][.].+")) throw new IllegalArgumentException();
      if (!(root.get("paths") instanceof Map<?, ?> paths)) throw new IllegalArgumentException();
      List<OnboardingPlan.Operation> result = new ArrayList<>();
      Set<String> ids = new HashSet<>();
      for (Map.Entry<?, ?> path : paths.entrySet()) {
        if (!(path.getKey() instanceof String template)
            || !template.startsWith("/")
            || template.length() > 512
            || template.matches(".*[?#\\s].*")) throw new IllegalArgumentException();
        if (!(path.getValue() instanceof Map<?, ?> methods) || methods.containsKey("$ref"))
          throw new IllegalArgumentException();
        for (Map.Entry<?, ?> entry : methods.entrySet()) {
          String method = String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT);
          if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method))
            continue;
          if (!(entry.getValue() instanceof Map<?, ?> operation)
              || !(operation.get("operationId") instanceof String id)
              || !id.matches("[A-Za-z0-9_.-]{1,128}")
              || !ids.add(id)) throw new IllegalArgumentException();
          result.add(new OnboardingPlan.Operation(id, method, template, true, List.of()));
        }
      }
      if (result.isEmpty() || result.size() > 1000) throw new IllegalArgumentException();
      return result.stream()
          .sorted(Comparator.comparing(OnboardingPlan.Operation::operationId))
          .toList();
    } catch (RuntimeException exception) {
      throw BusinessException.bad(
          "OPENAPI_INVALID", "请提供有效OpenAPI3 JSON/YAML，路径须内联且operationId唯一，最多1000项");
    }
  }
}
