package com.municipal.rbaccore.infrastructure.web;

import com.municipal.rbaccore.api.role.RoleController;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/** 角色接口独立绑定十进制参数，校验发生在Controller鉴权切面之外。 */
@ControllerAdvice(assignableTypes = RoleController.class)
public class RoleParameterBinding {
  /** 路径ID与分页字段沿原有类型绑定，并对原始格式使用同一规则。 */
  @InitBinder
  public void bindDecimalParameters(WebDataBinder binder) {
    binder.registerCustomEditor(Long.class, decimalEditor(Long.class));
    binder.registerCustomEditor(int.class, decimalEditor(Integer.class));
  }

  /** 先校验原始参数，再交给现有转换器检查整数范围。 */
  private CustomNumberEditor decimalEditor(Class<? extends Number> type) {
    return new CustomNumberEditor(type, false) {
      @Override
      public void setAsText(String text) {
        if (text == null || !text.matches("[0-9]+")) {
          throw new IllegalArgumentException("必须提供十进制整数");
        }
        super.setAsText(text);
      }
    };
  }
}
