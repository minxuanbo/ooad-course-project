package com.municipal.rbaccore.api.department;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.department.vo.OverviewVO;
import com.municipal.rbaccore.api.user.vo.DepartmentVO;
import com.municipal.rbaccore.service.port.department.OverviewResult;
import com.municipal.rbaccore.service.port.department.OverviewUseCase;
import com.municipal.rbaccore.service.port.security.Managed;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 范围总览的HTTP适配，独立于全局鉴权日志。 */
@RestController
@RequiredArgsConstructor
@Managed
public class OverviewController {
  private final OverviewUseCase service;

  /** 不接收客户端范围参数，身份和范围始终来自服务端。 */
  @GetMapping("/api/overview")
  public OverviewVO overview(
      @RequestHeader(value = "Authorization", required = false) String header) {
    OverviewResult result = service.overview(header);
    OverviewVO view = CloneFactory.copy(new OverviewVO(), result);
    view.setDistribution(
        result.getDistribution().stream()
            .map(item -> CloneFactory.copy(new DepartmentVO(), item))
            .toList());
    return view;
  }
}
