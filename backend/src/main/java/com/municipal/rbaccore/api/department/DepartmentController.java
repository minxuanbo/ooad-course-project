package com.municipal.rbaccore.api.department;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.api.department.dto.*;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.api.user.vo.DepartmentVO;
import com.municipal.rbaccore.service.port.department.*;
import com.municipal.rbaccore.service.port.security.Managed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 部门目录及新增、改名、删除空节点的HTTP适配。 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Managed
public class DepartmentController {
  private final DepartmentUseCase service;

  /** 层级导航完整返回范围树，人数由数据库聚合，不读取员工明细。 */
  @GetMapping
  public List<DepartmentVO> list(
      @RequestHeader(value = "Authorization", required = false) String header) {
    return service.list(header).stream()
        .map(item -> CloneFactory.copy(new DepartmentVO(), item))
        .toList();
  }

  /** DTO格式校验后映射为共享用例输入，返回已保存节点。 */
  @PostMapping
  public DepartmentVO create(
      @RequestHeader(value = "Authorization", required = false) String header,
      @Valid @RequestBody DepartmentCreateDTO dto) {
    return CloneFactory.copy(
        new DepartmentVO(),
        service.create(header, CloneFactory.copy(new CreateDepartmentCommand(), dto)));
  }

  /** 禁止通过改名入口修改父级或类型。 */
  @PatchMapping("/{id}")
  public DepartmentVO rename(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id,
      @Valid @RequestBody DepartmentRenameDTO dto) {
    return CloneFactory.copy(
        new DepartmentVO(),
        service.rename(header, id, CloneFactory.copy(new RenameDepartmentCommand(), dto)));
  }

  /** 删除失败保留节点及人员归属。 */
  @DeleteMapping("/{id}")
  public void delete(
      @RequestHeader(value = "Authorization", required = false) String header,
      @PathVariable @Positive @Max(ApiLimits.MAX_ID) Long id) {
    service.delete(header, id);
  }
}
