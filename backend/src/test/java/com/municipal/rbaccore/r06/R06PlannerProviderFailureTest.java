package com.municipal.rbaccore.r06;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.structured.bootstrap.RoleBootstrapPlanner;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** R06隔离文件读取端口，只验证摘要提供者故障的计划边界。 */
class R06PlannerProviderFailureTest {
  /** 不得在标准摘要不可用时返回可执行首次授权计划。 */
  @Test
  @DisplayName("R06 结构化角色预检摘要故障时停止计划")
  void R06_digestFailureDoesNotProduceExecutablePlan() throws Exception {
    RoleBootstrapPlanner planner = new RoleBootstrapPlanner(
        directory -> new ImportPlan(List.of(), List.of(), "org", "people"));
    try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
      digest.when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("provider unavailable"));
      assertEquals("运行环境缺少SHA-256", assertThrows(IllegalStateException.class,
          () -> planner.plan(Path.of("."))).getMessage());
    }
  }
}
