package com.municipal.rbaccore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R19/R21固定共享API、用例与结构化实现的单向依赖，防止迁移后重新混层。 */
class R19R21StructuredBoundaryTest {
  private static final String BASE = "com.municipal.rbaccore";
  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
          .importPackages(BASE);
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
    noClasses()
        .that()
        .resideInAPackage(BASE + ".api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            BASE + ".infrastructure..", BASE + ".service.structured..", BASE + ".service.ddd..")
        .check(CLASSES);
    int count = 0;
    for (JavaClass controller : CLASSES) {
      if (!controller.getPackageName().startsWith(BASE + ".api.")
          || !controller.getSimpleName().endsWith("Controller")) continue;
      assertControllerContract(controller.reflect());
      count++;
    }
    assertTrue(count > 0, "必须实际导入并检查生产Controller字节码");
  }

  /** 注释声称UseCase不能使错误的注入类型通过边界验证。 */
  @Test
  @DisplayName("R19/R21 注释不能掩盖控制器错误的依赖类型")
  void R19R21_commentCannotReplaceInjectedContract() {
    assertThrows(AssertionError.class, () -> assertControllerContract(MisleadingController.class));
  }

  /** UseCase只是误导性注释，该反例没有真正的公共用例接口依赖。 */
  private static class MisleadingController {
    private Object service;
  }

  /** 控制器约束集中于同一入口，反例与正式控制器执行相同验证。 */
  private void assertControllerContract(Class<?> controller) {
    int dependencies = 0;
    for (Field field : controller.getDeclaredFields()) {
      if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
      Class<?> type = field.getType();
      assertTrue(
          type.isInterface()
              && type.getPackageName().startsWith(BASE + ".service.port.")
              && type.getSimpleName().endsWith("UseCase"),
          controller.getName() + "." + field.getName() + " 必须注入公共用例接口");
      dependencies++;
    }
    assertTrue(dependencies > 0, controller + " 必须真实注入公共用例接口");
  }

  /** 用例契约不含JPA或Web类型，复制注解不能把Entity依赖带回端口。 */
  @Test
  @DisplayName("R19/R21 用例契约只表达命令结果和支撑抽象")
  void R19R21_portsContainNoPersistenceOrWebTypes() throws Exception {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".service.port..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            BASE + ".infrastructure..",
            BASE + ".service.structured..",
            BASE + ".service.ddd..",
            BASE + ".api..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "org.springframework..")
        .check(CLASSES);
  }

  /** 共享适配器不能调用业务编排，后续DDD持久化适配器仍可实现领域仓储并映射领域模型。 */
  @Test
  @DisplayName("R19/R21 共享基础设施不依赖结构化或DDD应用服务")
  void R19R21_sharedInfrastructureDoesNotDependOnBusinessImplementation() throws Exception {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(BASE + ".service.structured..", BASE + ".service.ddd.application..")
        .check(CLASSES);
  }

  /** 部门类型是HTTP与用例的共同取值，不承载必须由两种实现分别执行的层级策略。 */
  @Test
  @DisplayName("R19/R21 共享部门类型只声明取值，不携带层级策略")
  void R19R21_departmentTypeContainsOnlyContractValues() {
    for (java.lang.reflect.Method method :
        com.municipal.rbaccore.service.port.department.DepartmentType.class.getDeclaredMethods()) {
      if (method.isSynthetic()) continue;
      assertTrue(
          List.of("values", "valueOf").contains(method.getName()),
          "层级判断应归具体业务实现，不能放在公共枚举：" + method.getName());
    }
  }

  /** 结构化事务脚本可以直接使用JPA仓储，HTTP及Spring Security细节必须经支撑端口隔离。 */
  @Test
  @DisplayName("R19/R21 结构化服务不依赖HTTP模型及Web安全适配器")
  void R19R21_structuredServicesUseSupportPorts() throws Exception {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".service.structured..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            BASE + ".api..",
            "jakarta.servlet..",
            "org.springframework.web..",
            "org.springframework.security..",
            BASE + ".infrastructure.web..",
            BASE + ".infrastructure.security..")
        .check(CLASSES);
  }
}
