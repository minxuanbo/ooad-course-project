package com.municipal.rbaccore.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R19/R21固定共享API、用例与结构化实现的单向依赖，防止迁移后重新混层。 */
class R19R21StructuredBoundaryTest {
  private static final Path PROJECT =
      Files.isDirectory(Path.of("backend"))
          ? Path.of("")
          : Files.isDirectory(Path.of("../backend/src")) ? Path.of("..") : Path.of("");
  private static final Path SOURCE =
      PROJECT.resolve("backend/src/main/java/com/municipal/rbaccore");

  /** 后端独立归类且核心业务全部放在结构化层，本阶段没有DDD实现。 */
  @Test
  @DisplayName("R19/R21 后端独立归类且仅包含结构化业务实现")
  void R19R21_backendContainsOnlyStructuredBusiness() throws Exception {
    assertTrue(Files.isDirectory(SOURCE.resolve("service/structured")), "缺少独立backend及结构化业务层");
    assertFalse(Files.exists(SOURCE.resolve("service/ddd")), "本阶段不创建DDD业务代码");
    assertFalse(Files.exists(PROJECT.resolve("src/main/java")), "不得留下第二份后端源码");
  }

  /** Controller依赖可替换用例接口，不能引用实体、仓储或结构化实现。 */
  @Test
  @DisplayName("R19/R21 共享Controller只依赖公共用例接口和HTTP模型")
  void R19R21_controllersDependOnUseCases() throws Exception {
    Path api = SOURCE.resolve("api");
    assertTrue(Files.isDirectory(api), "缺少共享API层");
    for (Path file : javaFiles(api)) {
      if (!file.getFileName().toString().endsWith("Controller.java")) continue;
      String source = Files.readString(file);
      assertTrue(source.contains("UseCase"), file + " 必须依赖用例接口");
      assertFalse(source.contains(".infrastructure."), file + " 不得依赖基础设施");
      assertFalse(source.contains(".structured."), file + " 不得依赖具体业务实现");
    }
  }

  /** 用例契约不含JPA或Web类型，复制注解不能把Entity依赖带回端口。 */
  @Test
  @DisplayName("R19/R21 用例契约只表达命令结果和支撑抽象")
  void R19R21_portsContainNoPersistenceOrWebTypes() throws Exception {
    Path ports = SOURCE.resolve("service/port");
    assertTrue(Files.isDirectory(ports), "缺少共享用例契约");
    for (Path file : javaFiles(ports)) {
      String source = Files.readString(file);
      for (String forbidden :
          List.of(
              ".infrastructure.",
              "jakarta.persistence",
              "jakarta.servlet",
              "org.springframework",
              ".api.")) {
        assertFalse(source.contains(forbidden), file + " 不得包含 " + forbidden);
      }
      assertFalse(file.getFileName().toString().endsWith("Repository.java"), "持久化端口不混入用例契约");
    }
  }

  /** 结构化事务脚本可以直接使用JPA仓储，HTTP及Spring Security细节必须经支撑端口隔离。 */
  @Test
  @DisplayName("R19/R21 结构化服务不依赖HTTP模型及Web安全适配器")
  void R19R21_structuredServicesUseSupportPorts() throws Exception {
    Path structured = SOURCE.resolve("service/structured");
    assertTrue(Files.isDirectory(structured), "缺少结构化事务脚本");
    for (Path file : javaFiles(structured)) {
      String source = Files.readString(file);
      for (String forbidden :
          List.of(
              ".api.",
              "jakarta.servlet",
              "org.springframework.web",
              "org.springframework.security",
              "RequestAuditFilter",
              "BearerIdentityStore")) {
        assertFalse(source.contains(forbidden), file + " 不得包含 " + forbidden);
      }
    }
  }

  /** 关闭文件流，按真实Java文件检查边界而不依赖生成产物。 */
  private List<Path> javaFiles(Path directory) throws Exception {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(file -> file.toString().endsWith(".java")).toList();
    }
  }
}
