package com.municipal.rbaccore.shared;

import java.nio.file.Files;
import java.nio.file.Path;

/** 测试只定位当前仓库原件与部署资料，不把数据复制进后端或镜像。 */
public final class TestProjectPaths {
  private TestProjectPaths() {}

  /** Maven从backend执行，工具从仓库根执行时返回同一仓库。 */
  public static Path repository() {
    Path current = Path.of("").toAbsolutePath().normalize();
    return Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
  }

  /** 允许明确指定原件目录，默认保持仓库根三份Excel。 */
  public static Path sources() {
    String configured = System.getenv("RBAC_TEST_EXCEL_DIR");
    return configured == null || configured.isBlank() ? repository() : Path.of(configured);
  }
}
