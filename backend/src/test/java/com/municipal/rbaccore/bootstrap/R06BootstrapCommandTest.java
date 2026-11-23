package com.municipal.rbaccore.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.bootstrap.AuthorizationBootstrapUseCase;
import com.municipal.rbaccore.service.port.bootstrap.BootstrapReport;
import com.municipal.rbaccore.service.port.bootstrap.InitialRole;
import com.municipal.rbaccore.service.port.bootstrap.RoleBootstrapPlan;
import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import com.municipal.rbaccore.service.port.importing.ImportReport;
import com.municipal.rbaccore.service.port.importing.ImportUseCase;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/** R06离线命令边界测试：隔离启动器，真实执行CSV文件及上下文清理协议。 */
@ExtendWith(OutputCaptureExtension.class)
class R06BootstrapCommandTest {
  @TempDir
  Path temporary;

  /** 预检必须转义文本、保留来源行，并在结束后释放上下文。 */
  @Test
  @DisplayName("R06 预检CSV处理引号逗号及独立管理员，完成后关闭上下文")
  void R06_previewWritesEscapedCsv(CapturedOutput output) throws Exception {
    AuthorizationBootstrapUseCase service = mock(AuthorizationBootstrapUseCase.class);
    when(service.preview(temporary)).thenReturn(new RoleBootstrapPlan(null,
        List.of(new RoleBootstrapPlan.Assignment("001,\"甲", 7, InitialRole.EMPLOYEE)),
        Map.of("employee", 1L), "manifest"));
    when(service.initialAdminUsername()).thenReturn("root,\"admin");
    Path csv = temporary.resolve("preview.csv");
    GenericApplicationContext context = authorizationContext(service, csv.toString());
    try (MockedConstruction<SpringApplication> applications = launcher(context)) {
      BootstrapAuthorizationCommand.main(new String[] {"--trace=false"});
      List<String> lines = Files.readAllLines(csv);
      assertEquals("username,role,sourceRow", lines.getFirst());
      assertEquals("\"001,\"\"甲\",\"" + InitialRole.EMPLOYEE.getName() + "\",7", lines.get(1));
      assertEquals("\"root,\"\"admin\",\"" + InitialRole.SYSTEM_ADMIN.getName() + "\",", lines.get(2));
      assertTrue(output.getOut().contains("未写入授权"));
      assertTrue(output.getOut().contains("manifest"));
      verify(service, never()).initialize(any());
      verify(applications.constructed().getFirst()).setWebApplicationType(WebApplicationType.NONE);
    }
    assertFalse(context.isActive());
  }

  /** 已有文件不能覆盖，格式错误也不得生成文件或执行初始化。 */
  @Test
  @DisplayName("R06 预检拒绝覆盖已有清单及非CSV路径，失败仍关闭上下文")
  void R06_previewProtectsExistingFiles() throws Exception {
    for (String name : List.of("existing.csv", "wrong.txt")) {
      Path file = temporary.resolve(name);
      Files.writeString(file, "原清单");
      AuthorizationBootstrapUseCase service = mock(AuthorizationBootstrapUseCase.class);
      when(service.preview(temporary)).thenReturn(new RoleBootstrapPlan(null, List.of(), Map.of(), "hash"));
      GenericApplicationContext context = authorizationContext(service, file.toString());
      try (MockedConstruction<SpringApplication> ignored = launcher(context)) {
        if (name.endsWith(".csv")) {
          assertThrows(FileAlreadyExistsException.class,
              () -> BootstrapAuthorizationCommand.main(new String[0]));
        } else {
          assertThrows(IllegalArgumentException.class,
              () -> BootstrapAuthorizationCommand.main(new String[0]));
        }
        assertEquals("原清单", Files.readString(file));
        verify(service, never()).initialize(any());
      }
      assertFalse(context.isActive());
    }
  }

  /** 未指定预检输出时执行首次发布，只输出脱敏报告。 */
  @Test
  @DisplayName("R06 正式初始化分支输出回执且不生成预检文件")
  void R06_initializeUsesConfirmedDirectory(CapturedOutput output) throws Exception {
    AuthorizationBootstrapUseCase service = mock(AuthorizationBootstrapUseCase.class);
    when(service.initialize(temporary)).thenReturn(new BootstrapReport(Map.of(), 2, false, "confirmed"));
    GenericApplicationContext context = authorizationContext(service, null);
    try (MockedConstruction<SpringApplication> ignored = launcher(context)) {
      BootstrapAuthorizationCommand.main(new String[0]);
      assertTrue(output.getOut().contains("manifestSha256=confirmed"));
      verify(service).initialize(temporary);
      verify(service, never()).preview(any());
    }
    assertFalse(context.isActive());
  }

  /** 口令由环境传入用例，导入报告及命令消息不得包含明文。 */
  @Test
  @DisplayName("R03 离线导入口令和目录正确传入，输出不泄露口令")
  void R03_importReadsCredentialsAndClosesContext(CapturedOutput output) {
    ImportUseCase service = mock(ImportUseCase.class);
    when(service.importDirectory(any(), any())).thenReturn(new ImportReport(1, 1, 1, 2, 0, 2L, "org", "users"));
    MockEnvironment environment = new MockEnvironment()
        .withProperty("rbac.core.import.employee-password", "employee-test-secret")
        .withProperty("rbac.core.import.admin-password", "admin-test-secret")
        .withProperty("rbac.core.import.source-dir", temporary.toString());
    GenericApplicationContext context = new GenericApplicationContext();
    context.setEnvironment(environment);
    context.registerBean(ImportUseCase.class, () -> service);
    context.refresh();
    try (MockedConstruction<SpringApplication> applications = launcher(context)) {
      ImportCommand.main(new String[0]);
      ArgumentCaptor<BootstrapCredentials> credentials = ArgumentCaptor.forClass(BootstrapCredentials.class);
      verify(service).importDirectory(eq(temporary), credentials.capture());
      assertEquals("admin", credentials.getValue().getAdminUsername());
      assertEquals("employee-test-secret", credentials.getValue().getEmployeePassword());
      assertEquals("admin-test-secret", credentials.getValue().getAdminPassword());
      assertFalse(output.getOut().contains("test-secret"));
      assertTrue(output.getOut().contains("organizationSha256=org"));
      verify(applications.constructed().getFirst()).setWebApplicationType(WebApplicationType.NONE);
    }
    assertFalse(context.isActive());
  }

  /** 产品入口保持Servlet类型和独立配置，不意外变成离线导入。 */
  @Test
  @DisplayName("R07 产品main以Servlet和运行配置启动")
  void R07_productMainUsesRuntimeConfiguration() {
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();
    try (context; MockedConstruction<SpringApplication> applications = launcher(context)) {
      RbacApplication.main(new String[] {"--server.port=0"});
      SpringApplication application = applications.constructed().getFirst();
      verify(application).setWebApplicationType(WebApplicationType.SERVLET);
      verify(application).setDefaultProperties(Map.of("spring.config.name", "application-core-runtime"));
      verify(application).run(new String[] {"--server.port=0"});
    }
  }

  /** 使用真实轻量上下文装配命令端口；这里只替换启动器，不模拟数据库。 */
  private GenericApplicationContext authorizationContext(AuthorizationBootstrapUseCase service, String preview) {
    MockEnvironment environment = new MockEnvironment().withProperty("rbac.core.source-dir", temporary.toString());
    if (preview != null) environment.setProperty("rbac.core.preview-file", preview);
    GenericApplicationContext context = new GenericApplicationContext();
    context.setEnvironment(environment);
    context.registerBean(AuthorizationBootstrapUseCase.class, () -> service);
    context.refresh();
    return context;
  }

  /** 截止于进程启动边界，确保真实端口调用和文件副作用由命令本身完成。 */
  private MockedConstruction<SpringApplication> launcher(GenericApplicationContext context) {
    return mockConstruction(SpringApplication.class,
        (application, construction) -> when(application.run(any(String[].class))).thenReturn(context));
  }
}
