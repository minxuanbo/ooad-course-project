package com.municipal.rbaccore.service.port.security;

/** 请求支撑仅提供服务端操作及可信主体，不泄漏Servlet。 */
public interface RequestContext {

  /** 更新已核实主体以供最小日志采集，不记录原始登录输入。 */
  void actor(Long id, String username);

  /** 登记服务端核实的应用受众，用于安全日志。 */
  void application(String code);

  /** 登记实际受控操作的最终访问判定，供统一过滤器去重记录。 */
  void authorizationDecision(boolean allowed);

  /** 登记权限查询的真假结果，不冒充目标业务已经执行或被业务HTTP拒绝。 */
  void permissionCheck(boolean allowed);

  /** 获取当前请求关联号，无HTTP请求时为空字符串。 */
  String requestId();

  /** 只登记稳定错误码以供最终安全事件采集。 */
  void error(String code);

  /** 返回当前服务端匹配操作；无HTTP上下文时返回null。 */
  Operation operation();

  /** 只含服务端方法与模板的纯值。 */
  record Operation(String method, String template) {}
}
