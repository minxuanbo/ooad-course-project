package com.municipal.rbaccore.service.port.security;

/** 请求支撑仅提供服务端操作及可信主体，不泄漏Servlet。 */
public interface RequestContext {

  /** 更新已核实主体以供最小日志采集，不记录原始登录输入。 */
  void actor(Long id, String username);

  /** 获取当前请求关联号，无HTTP请求时为空字符串。 */
  String requestId();

  /** 只登记稳定错误码以供最终安全事件采集。 */
  void error(String code);

  /** 返回当前服务端匹配操作；无HTTP上下文时返回null。 */
  Operation operation();

  /** 只含服务端方法与模板的纯值。 */
  record Operation(String method, String template) {}
}
