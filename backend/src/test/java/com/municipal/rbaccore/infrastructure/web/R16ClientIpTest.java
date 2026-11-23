package com.municipal.rbaccore.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 代理边界验证IPv4/IPv6、伪造头和配置错误，不进行外部DNS或HTTP访问。 */
class R16ClientIpTest {
  /** 实际过滤器把核实来源附加至最终安全事件，未建立请求上下文时不伪造来源。 */
  @Test
  void filterPersistsTrustedSource() throws Exception {
    com.municipal.rbaccore.service.port.audit.AuditWriter writer = org.mockito.Mockito.mock(com.municipal.rbaccore.service.port.audit.AuditWriter.class);
    RequestAuditFilter filter = new RequestAuditFilter(writer, new ClientIpResolver("10.20.0.0/16"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
    request.setRemoteAddr("10.20.1.2");
    request.addHeader("X-Forwarded-For", "203.0.113.8");
    filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (incoming, response) -> {});
    org.mockito.ArgumentCaptor<com.municipal.rbaccore.service.port.audit.AuditEvent> event = org.mockito.ArgumentCaptor.forClass(com.municipal.rbaccore.service.port.audit.AuditEvent.class);
    org.mockito.Mockito.verify(writer).record(event.capture());
    assertEquals("203.0.113.8", event.getValue().clientIp());
    assertNull(RequestAuditFilter.clientIp());
  }
  /** 没有显式可信代理时只接受连接来源，外部转发头均不能改变结果。 */
  @Test
  void rejectsUntrustedForwarding() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("X-Forwarded-For", "203.0.113.4");
    assertEquals("192.0.2.10", new ClientIpResolver("").resolve(request));
    assertEquals("192.0.2.10", new ClientIpResolver("10.20.0.0/16,2001:db8::/32").resolve(request));
  }

  /** 可信边界只接受单个合法字面量，链式、重复及带端口的来源退回连接地址。 */
  @Test
  void acceptsOnlyOneValidatedSource() {
    ClientIpResolver resolver = new ClientIpResolver("10.20.0.0/16");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.20.1.2");
    assertEquals("10.20.1.2", resolver.resolve(request));
    for (String invalid : List.of("example.test", "203.0.113.5, 10.20.0.2", "1.2.3.999", "1.2.3",
        "203.0.113.5:99", "fe80::1%eth0", "z::1", "1:::2", "x".repeat(46), "")) {
      request.removeHeader("X-Forwarded-For");
      request.addHeader("X-Forwarded-For", invalid);
      assertEquals("10.20.1.2", resolver.resolve(request), invalid);
    }
    request.removeHeader("X-Forwarded-For");
    request.addHeader("X-Forwarded-For", "203.0.113.5");
    assertEquals("203.0.113.5", resolver.resolve(request));
    request.addHeader("X-Forwarded-For", "198.51.100.6");
    assertEquals("10.20.1.2", resolver.resolve(request));
    request.setRemoteAddr(null);
    assertNull(resolver.resolve(request));
  }

  /** IPv6按地址族及CIDR匹配，不以字符串前缀或主机名判断可信。 */
  @Test
  void validatesIpv6AndConfiguration() {
    ClientIpResolver resolver = new ClientIpResolver("2001:db8::/32");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("2001:db8:1::2");
    request.addHeader("X-Forwarded-For", "2001:db9::3");
    assertEquals("2001:db9:0:0:0:0:0:3", resolver.resolve(request));
    request.setRemoteAddr("2001:db9::2");
    assertEquals("2001:db9:0:0:0:0:0:2", resolver.resolve(request));
    for (String invalid : List.of("localhost/32", "10.0.0.0", "10.0.0.0/33", "::/129", "::/-1",
        "::/x", "::/1/2", "10.0.0.0/8,", "/8", "::/9999")) {
      assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver(invalid), invalid);
    }
  }
}
