package com.municipal.rbaccore.infrastructure.excel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.service.port.error.ImportException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R03来源列编码的技术边界，不包含导入业务或数据库替身。 */
class R03SourceCellsJsonTest {
  /** 固定列顺序、前导零和特殊字符往返不变，坏JSON不伪造成正常来源。 */
  @Test
  @DisplayName("R03 来源JSON保留原始列并拒绝损坏内容")
  void R03_preservesSourceColumnsAndDetectsDamage() {
    SourceCellsJson codec = new SourceCellsJson();
    List<String> cells = List.of("张三", "00123", "引号\"和换行\n");
    assertEquals(cells, codec.decode(codec.encode(cells)));
    assertEquals(List.of(), codec.decode("{broken"));
  }

  /** 编码异常只翻译稳定错误，不泄漏输入或底层序列化消息。 */
  @Test
  @DisplayName("R03 来源编码故障保留错误契约且隐藏人员资料")
  void R03_encodingFailureHidesSourceData() throws Exception {
    ObjectMapper mapper = mock(ObjectMapper.class);
    when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("private-source") {});
    ImportException failure = assertThrows(ImportException.class,
        () -> new SourceCellsJson(mapper).encode(List.of("private-source")));
    assertEquals("SOURCE_FIELD_INVALID", failure.getCode());
    assertFalse(failure.getMessage().contains("private-source"));
  }
}
