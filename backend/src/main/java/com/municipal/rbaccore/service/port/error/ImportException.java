package com.municipal.rbaccore.service.port.error;

import lombok.Getter;

/** 离线导入的稳定业务错误；消息只含来源位置，不暴露口令和数据库细节。 */
@Getter
public class ImportException extends RuntimeException {
  private final String code;

  /** 保存可供命令和测试识别的错误码及中文说明。 */
  public ImportException(String code, String message) {
    super(message);
    this.code = code;
  }
}
