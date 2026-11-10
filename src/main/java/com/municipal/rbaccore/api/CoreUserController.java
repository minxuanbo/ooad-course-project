package com.municipal.rbaccore.api;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.dto.UserQueryDTO;
import com.municipal.rbaccore.api.vo.CoreViews;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.CoreResults;
import com.municipal.rbaccore.service.CoreUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本轮唯一人员列表及部门导航入口，不保留第二套正式分页接口。 */
@RestController
@RequestMapping("/api")
@CoreManaged
@RequiredArgsConstructor
public class CoreUserController {
  private final CoreUserService service;

  /** 先在API边界校验参数，再映射成独立查询命令。 */
  @GetMapping("/users")
  public CoreViews.UserPageVO users(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @ModelAttribute UserQueryDTO dto) {
    CoreResults.UserSearch command = CloneFactory.copy(new CoreResults.UserSearch(), dto);
    command.setEnabled(dto.getEnabled() == null ? null : Boolean.valueOf(dto.getEnabled()));
    CoreResults.UserPageResult result = service.list(header, command);
    CoreViews.UserPageVO view = CloneFactory.copy(new CoreViews.UserPageVO(), result);
    view.setItems(
        result.getItems().stream()
            .map(item -> CloneFactory.copy(new CoreViews.UserVO(), item))
            .toList());
    return view;
  }

  /** 返回可见范围的扁平树，客户端不得自行裁剪全部组织。 */
  @GetMapping("/departments/tree")
  public List<CoreViews.DepartmentVO> tree(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.tree(header).stream()
        .map(item -> CloneFactory.copy(new CoreViews.DepartmentVO(), item))
        .toList();
  }
}
