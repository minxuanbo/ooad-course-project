package com.municipal.rbaccore.api.audit.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.audit.LogQuery;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 最小日志参数校验；不接受storage归档筛选或导出参数。 */
@Getter
@Setter
@NoArgsConstructor
@CopyTo(LogQuery.class)
public class LogQueryDTO {
  @Positive private Long userId;

  @Size(max = 100)
  private String username;

  @Size(max = 191)
  private String appCode;

  @Pattern(regexp = "true|false")
  @CopyTo.Exclude(LogQuery.class)
  private String allowed;

  @CopyTo.Exclude(LogQuery.class)
  private String from;

  @CopyTo.Exclude(LogQuery.class)
  private String to;

  @Size(max = 32)
  private String event;

  @Size(max = 4096)
  private String cursor;

  @Min(1)
  @Max(500)
  private int limit = 100;

  /** 时间必须是UTC ISO-8601；区间起点含、终点不含且起点早于终点。 */
  @AssertTrue(message = "时间须为UTC ISO-8601且from早于to")
  public boolean isTimeRangeValid() {
    try {
      Instant start = parseUtc(from);
      Instant end = parseUtc(to);
      return start == null || end == null || start.isBefore(end);
    } catch (DateTimeParseException exception) {
      return false;
    }
  }

  /** 校验与转换共用解析规则，保留精确UTC时间含义。 */
  public static Instant parseUtc(String value) {
    if (value == null) {
      return null;
    }
    if (!value.endsWith("Z")) {
      throw new DateTimeParseException("须使用UTC", value, 0);
    }
    return Instant.parse(value);
  }
}
