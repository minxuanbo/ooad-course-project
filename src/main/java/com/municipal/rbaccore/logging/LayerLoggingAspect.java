package com.municipal.rbaccore.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** API、Service和运行适配的集中日志，只输出方法、耗时和异常类型。 */
@Aspect
@Component
public class LayerLoggingAspect {
  private static final Logger LOG = LoggerFactory.getLogger(LayerLoggingAspect.class);

  /** 禁止打印参数、对象、响应体、令牌、SQL或原异常消息。 */
  @Around(
      "within(com.municipal.rbaccore.api..*) || within(com.municipal.rbaccore.service..*) ||"
          + " (within(com.municipal.rbaccore.logging..*) &&"
          + " !within(com.municipal.rbaccore.logging.LayerLoggingAspect)) ||"
          + " within(com.municipal.rbaccore.bootstrap..*)")
  public Object log(ProceedingJoinPoint point) throws Throwable {
    long start = System.nanoTime();
    String method =
        point.getSignature().getDeclaringTypeName() + "." + point.getSignature().getName();
    try {
      Object result = point.proceed();
      LOG.debug("完成 {} 耗时{}微秒", method, (System.nanoTime() - start) / 1000);
      return result;
    } catch (Throwable exception) {
      LOG.debug("失败 {} 异常类型{}", method, exception.getClass().getSimpleName());
      throw exception;
    }
  }

  /** 鉴权日志存储故障由唯一集中入口报告，不包含任何业务记录内容。 */
  @EventListener
  public void writeFailure(CoreAuditWriter.WriteFailure failure) {
    LOG.warn("最小鉴权日志写入失败，异常类型{}", failure.exceptionType());
  }
}
