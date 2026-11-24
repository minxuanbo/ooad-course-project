package com.municipal.rbaccore.infrastructure.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;

/** 仅供本机真实身份协议探测，调用地址由正在运行的服务端口构造。 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface IdentityProtocolClient {
  /** 建立专用测试应用会话。 */
  @PostExchange("/auth/login")
  ResponseEntity<JsonNode> login(
  @RequestBody
  LoginBody body);

  /** 读取当前受众及真实有效权限。 */
  @GetExchange("/auth/me")
  ResponseEntity<JsonNode> me(
  @RequestHeader("Authorization")
  String authorization);

  /** 外部身份不得进入RBAC管理受众。 */
  @GetExchange("/api/me")
  ResponseEntity<JsonNode> management(
  @RequestHeader("Authorization")
  String authorization);

  /** 使用随机未知短码验证拒绝，不撤回真实角色。 */
  @GetExchange("/auth/permissions/{permission}")
  ResponseEntity<JsonNode> permission(
      @RequestHeader("Authorization")
      String authorization,
      @PathVariable("permission")
      String permission);

  /** 撤销本次会话。 */
  @PostExchange("/auth/logout")
  ResponseEntity<Void> logout(
  @RequestHeader("Authorization")
  String authorization);

  /** 凭据只用于单次HTTP报文，不持久化或输出。 */
  record LoginBody(String username, String password, List<String> systems) {}
}
