package com.municipal.rbaccore.api.resource;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.resource.dto.SystemCreateDTO;
import com.municipal.rbaccore.api.resource.dto.SystemPatchDTO;
import com.municipal.rbaccore.api.resource.dto.SystemQueryDTO;
import com.municipal.rbaccore.api.resource.vo.SystemPageVO;
import com.municipal.rbaccore.api.resource.vo.SystemVO;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.resource.CreateSystemCommand;
import com.municipal.rbaccore.service.port.resource.ResourceUseCase;
import com.municipal.rbaccore.service.port.resource.SystemPageResult;
import com.municipal.rbaccore.service.port.resource.SystemQuery;
import com.municipal.rbaccore.service.port.resource.UpdateSystemCommand;
import com.municipal.rbaccore.service.port.security.Managed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 应用管理HTTP入口，业务服务负责根范围、锁后重鉴权及维护者保护。 */
@RestController
@RequestMapping("/api/systems")
@Managed
@RequiredArgsConstructor
public class ApplicationController {
  private final ResourceUseCase service;

  /** 可扩张的维护列表单独分页，不改变既有选择器目录响应。 */
  @GetMapping("/page")
  public SystemPageVO page(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @Valid
      @ModelAttribute
      SystemQueryDTO dto) {
    SystemQuery query = CloneFactory.copy(new SystemQuery(), dto);
    query.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    SystemPageResult result = service.searchSystems(header, query);
    SystemPageVO view = CloneFactory.copy(new SystemPageVO(), result);
    view.setItems(
        result.getItems().stream().map(item -> CloneFactory.copy(new SystemVO(), item)).toList());
    return view;
  }

  /** 创建待接入目录，稳定编码与名称由请求模型先行校验。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SystemVO create(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @Valid
      @RequestBody
      SystemCreateDTO dto) {
    return CloneFactory.copy(
        new SystemVO(),
        service.createSystem(header, CloneFactory.copy(new CreateSystemCommand(), dto)));
  }

  /** 可编辑名称或接入状态，恢复应用不恢复曾被人工撤销的角色权限。 */
  @PatchMapping("/{id}")
  public SystemVO patch(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id,
      @Valid
      @RequestBody
      SystemPatchDTO dto) {
    return CloneFactory.copy(
        new SystemVO(),
        service.patchSystem(header, id, CloneFactory.copy(new UpdateSystemCommand(), dto)));
  }

  /** 移除采用停用接入，保留目录、角色映射及历史日志，下一请求即时拒绝。 */
  @DeleteMapping("/{id}")
  public SystemVO remove(
      @RequestHeader(value = "Authorization", required = false)
      String header,
      @PathVariable
      @Positive
      @Max(ApiLimits.MAX_ID)
      Long id) {
    return CloneFactory.copy(new SystemVO(), service.removeSystem(header, id));
  }
}
