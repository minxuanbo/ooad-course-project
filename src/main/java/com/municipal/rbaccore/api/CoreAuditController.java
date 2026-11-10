package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.LogQueryDTO;
import com.municipal.rbaccore.api.vo.LogViews;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.CoreAuditService;
import com.municipal.rbaccore.service.LogModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 最小鉴权日志仅开放GET查询，归档、导出、完整审计不进入本轮。 */
@RestController
@RequestMapping("/api/authorization-logs")
@CoreManaged
@RequiredArgsConstructor
public class CoreAuditController {
  private final CoreAuditService service;

  /** 校验与类型转换在API边界完成，服务负责权限范围和快照一致性。 */
  @GetMapping
  public LogViews.PageVO list(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute LogQueryDTO dto) {
    LogModels.Search command = CloneFactory.copy(new LogModels.Search(), dto);
    command.setAllowed(dto.getAllowed() == null ? null : Boolean.valueOf(dto.getAllowed()));
    command.setFrom(LogQueryDTO.parseUtc(dto.getFrom()));
    command.setTo(LogQueryDTO.parseUtc(dto.getTo()));
    LogModels.PageResult result = service.list(header, command);
    LogViews.PageVO view = CloneFactory.copy(new LogViews.PageVO(), result);
    view.setItems(
        result.getItems().stream()
            .map(
                item -> {
                  LogViews.EntryVO entry = CloneFactory.copy(new LogViews.EntryVO(), item);
                  entry.setOccurredAt(item.getOccurredAt().toString());
                  return entry;
                })
            .toList());
    return view;
  }
}
