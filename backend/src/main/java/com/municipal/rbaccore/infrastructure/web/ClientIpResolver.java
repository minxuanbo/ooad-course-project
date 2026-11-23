package com.municipal.rbaccore.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 仅显式可信代理可以转交单一来源IP；不解析主机名、端口或客户端伪造的地址链。 */
@Component
public class ClientIpResolver {
  private final List<Network> trusted;

  /** 配置为空时只记录实际连接方；错误网段使启动失败，不能静默放宽信任。 */
  public ClientIpResolver(
      @Value("${app.audit.trusted-proxies:}")
      String networks) {
    trusted = networks.isBlank() ? List.of()
        : Arrays.stream(networks.split(",", -1)).map(Network::parse).toList();
  }

  /** 代理必须覆盖公网输入；多地址或非法转发头均退回实际连接方。 */
  public String resolve(HttpServletRequest request) {
    InetAddress peer = literal(request.getRemoteAddr());
    if (peer == null) return null;
    if (trusted.stream().anyMatch(network -> network.contains(peer))) {
      List<String> forwarded = java.util.Collections.list(request.getHeaders("X-Forwarded-For"));
      if (forwarded.size() == 1) {
        InetAddress source = literal(forwarded.getFirst());
        if (source != null) return source.getHostAddress();
      }
    }
    return peer.getHostAddress();
  }

  /** 只接受IP字面量并禁用IPv6区域标识，避免DNS查询或日志注入。 */
  private static InetAddress literal(String value) {
    if (value == null || value.length() > 45) return null;
    if (value.contains(":")) {
      if (!value.matches("[0-9A-Fa-f:.]+")) return null;
    } else {
      if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
      for (String part : value.split("\\.")) {
        if (Integer.parseInt(part) > 255) return null;
      }
    }
    try {
      return InetAddress.getByName(value);
    } catch (UnknownHostException exception) {
      return null;
    }
  }

  /** 不可变网段只保存解析后的地址；掩码匹配同时检查IPv4/IPv6族。 */
  private record Network(byte[] address, int prefix) {
    /** 配置只接受显式CIDR，不接受主机名、空项或超出地址族的掩码。 */
    private static Network parse(String configured) {
      String[] parts = configured.trim().split("/", -1);
      InetAddress address = literal(parts[0]);
      if (parts.length != 2 || address == null || !parts[1].matches("[0-9]{1,3}")) {
        throw new IllegalArgumentException("可信代理必须配置有效IP/CIDR");
      }
      int prefix = Integer.parseInt(parts[1]);
      if (prefix > address.getAddress().length * 8) {
        throw new IllegalArgumentException("可信代理CIDR掩码超出地址族范围");
      }
      return new Network(address.getAddress(), prefix);
    }

    /** 逐位比较前缀，不将不同地址族或相同文本前缀误判为可信。 */
    private boolean contains(InetAddress candidate) {
      byte[] actual = candidate.getAddress();
      if (address.length != actual.length) return false;
      for (int bit = 0; bit < prefix; bit++) {
        int mask = 1 << (7 - bit % 8);
        if ((address[bit / 8] & mask) != (actual[bit / 8] & mask)) return false;
      }
      return true;
    }
  }
}
