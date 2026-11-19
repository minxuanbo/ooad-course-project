package com.municipal.rbaccore.api.course.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import com.municipal.rbaccore.service.port.course.CourseContract;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接收老师现有统一身份登录协议，不接受其他系统或客户端自报角色。 */
@Getter
@Setter
@NoArgsConstructor
@CopyTo(LoginCommand.class)
public class CourseLoginDTO {
  @NotBlank
  @Size(max = 100)
  private String username;

  @NotNull
  @Size(min = 1, max = 200)
  private String password;

  @NotNull
  @Size(min = 1, max = 1)
  private List<String> systems;

  /** 本轮只接入课程系统，缺少或伪造受众均属于请求格式错误。 */
  @AssertTrue
  public boolean isCourseSystem() {
    return systems != null && systems.equals(List.of(CourseContract.SYSTEM_CODE));
  }
}
