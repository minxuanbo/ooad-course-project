package com.municipal.rbaccore.infrastructure.excel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.service.port.error.ImportException;
import java.util.List;
import org.springframework.stereotype.Component;

/** 两策略共用的原始来源列JSON编码器，不承载导入或授权业务判断。 */
@Component
public class SourceCellsJson {
  private static final TypeReference<List<String>> CELLS = new TypeReference<>() {};
  private final ObjectMapper mapper;

  /** 来源文档沿用独立默认编码设置，不受HTTP字段强制转换策略影响。 */
  public SourceCellsJson() {
    this(new ObjectMapper());
  }

  /** 显式编码依赖用于技术故障测试，不改变来源的业务核验规则。 */
  public SourceCellsJson(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** 原始列只保存来源资料，编码故障不输出人员内容。 */
  public String encode(List<String> cells) {
    try {
      return mapper.writeValueAsString(cells);
    } catch (JsonProcessingException exception) {
      throw new ImportException("SOURCE_FIELD_INVALID", "来源字段无法保存");
    }
  }

  /** 损坏来源退回空列，由后续来源一致性核验明确拒绝。 */
  public List<String> decode(String json) {
    try {
      return mapper.readValue(json, CELLS);
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }
}
