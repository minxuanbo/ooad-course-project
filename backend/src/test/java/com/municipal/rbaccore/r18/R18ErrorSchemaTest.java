package com.municipal.rbaccore.r18;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.shared.TestProjectPaths;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R18保证可公开的路由及组织业务错误均属于机器契约，避免客户端将合法错误当作未知响应。 */
class R18ErrorSchemaTest {
  /** 路由404/405和部门规则错误保持独立语义，不能为了迎合旧枚举而归并为参数错误。 */
  @Test
  @DisplayName("R18 OpenAPI声明路由及部门管理的全部专用错误码")
  void R18_errorSchemaContainsRoutingAndDepartmentCodes() throws Exception {
    JsonNode schema =
        new ObjectMapper()
            .readTree(Files.readString(TestProjectPaths.repository().resolve("api/openapi.json")))
            .at("/components/schemas/Error/properties/code/enum");
    List<String> codes =
        StreamSupport.stream(schema.spliterator(), false).map(JsonNode::asText).toList();
    for (String code :
        List.of(
            "ENDPOINT_NOT_FOUND",
            "METHOD_NOT_ALLOWED",
            "DEPARTMENT_TYPE_INVALID",
            "DEPARTMENT_ROOT_REQUIRED",
            "DEPARTMENT_NOT_EMPTY",
            "DEPARTMENT_NAME_CONFLICT")) {
      assertTrue(codes.contains(code), "OpenAPI未声明实际HTTP错误码：" + code);
    }
  }
}
