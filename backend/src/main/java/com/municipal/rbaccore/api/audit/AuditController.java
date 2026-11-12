package com.municipal.rbaccore.api.audit;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.audit.dto.LogQueryDTO;
import com.municipal.rbaccore.api.audit.vo.LogEntryVO;
import com.municipal.rbaccore.api.audit.vo.LogPageVO;
import com.municipal.rbaccore.service.port.audit.AuditLogUseCase;
import com.municipal.rbaccore.service.port.audit.LogPageResult;
import com.municipal.rbaccore.service.port.audit.LogQuery;
import com.municipal.rbaccore.service.port.security.Managed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 最小鉴权日志仅开放GET查询，归档、导出、完整审计不进入本轮。 */
@RestController
@RequestMapping("/api/authorization-logs")
@Managed
@RequiredArgsConstructor
public class AuditController {
  private final AuditLogUseCase service;

  /** 校验与类型转换在API边界完成，服务负责权限范围和快照一致性。 */
  @GetMapping
  public LogPageVO list(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute LogQueryDTO dto) {
    LogQuery command = CloneFactory.copy(new LogQuery(), dto);
    command.setAllowed(dto.getAllowed() == null ? null : Boolean.valueOf(dto.getAllowed()));
    command.setFrom(LogQueryDTO.parseUtc(dto.getFrom()));
    command.setTo(LogQueryDTO.parseUtc(dto.getTo()));
    LogPageResult result = service.list(header, command);
    LogPageVO view = CloneFactory.copy(new LogPageVO(), result);
    view.setItems(
        result.getItems().stream()
            .map(
                item -> {
                  LogEntryVO entry = CloneFactory.copy(new LogEntryVO(), item);
                  entry.setOccurredAt(item.getOccurredAt().toString());
                  return entry;
                })
            .toList());
    return view;
  }
}
