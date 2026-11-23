package com.municipal.rbaccore.service.port.resource;

/** 目录预登记与实际接入分开，待接入应用只能规划授权。 */
public enum ConnectionStatus {
  PENDING,
  CONNECTED
}
