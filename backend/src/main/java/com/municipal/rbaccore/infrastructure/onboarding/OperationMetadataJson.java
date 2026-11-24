package com.municipal.rbaccore.infrastructure.onboarding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.service.port.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 操作条件角色集合的技术序列化，不根据角色编码决定业务资格。 */
@Component
@RequiredArgsConstructor
public class OperationMetadataJson {
  private final ObjectMapper mapper;

  /** 元数据损坏时拒绝读取，不能静默变成无全局门禁。 */
  public List<String> read(String json) {
    try {
      List<String> result = mapper.readValue(json, new TypeReference<List<String>>() {});
      if (result == null
          || result.stream()
              .anyMatch(role -> role == null || !role.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))
          || new java.util.HashSet<>(result).size() != result.size())
        throw new BusinessException(409, "DATA_CONFLICT", "接入操作元数据损坏");
      return result;
    } catch (JsonProcessingException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "接入操作元数据损坏");
    }
  }

  /** 排序使相同配置的摘要不受角色输入顺序影响。 */
  public String write(List<String> roles) {
    try {
      return mapper.writeValueAsString(roles.stream().sorted().toList());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("接入操作元数据序列化失败", exception);
    }
  }
}
