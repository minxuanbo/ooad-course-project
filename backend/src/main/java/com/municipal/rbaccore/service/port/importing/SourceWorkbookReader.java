package com.municipal.rbaccore.service.port.importing;

/** 原始资料读取抽象，结构化用例不负责XLSX格式解析。 */
public interface SourceWorkbookReader {
  /** 返回完整校验后的来源计划，读取失败不返回部分计划。 */
  ImportPlan read(java.nio.file.Path directory);
}
