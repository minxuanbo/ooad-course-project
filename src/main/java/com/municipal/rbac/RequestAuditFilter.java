package com.municipal.rbac;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.context.request.*;

/** Never stores passwords, tokens, request bodies, or query strings. */
@Component
class RequestAuditFilter extends OncePerRequestFilter {
  private final AuditRecorder recorder;
  RequestAuditFilter(AuditRecorder recorder){this.recorder=recorder;}
  static HttpServletRequest current(){var context=RequestContextHolder.getRequestAttributes();return context instanceof ServletRequestAttributes a?a.getRequest():null;}
  static String requestId(){var r=current();return r==null?"":String.valueOf(r.getAttribute("requestId"));}
  static void error(String code){var r=current();if(r!=null)r.setAttribute("errorCode",code);}
  static void actor(Long id,String account){var r=current();if(r!=null){r.setAttribute("auditUserId",id);r.setAttribute("auditAccount",account);}}
  protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    String id=UUID.randomUUID().toString();request.setAttribute("requestId",id);response.setHeader("X-Request-Id",id);
    try{chain.doFilter(request,response);}finally{
      String path=request.getRequestURI();int status=response.getStatus();
      boolean login=path.equals("/api/auth/login")&&request.getMethod().equals("POST");
      if(path.startsWith("/api/")&&(login||status==401||status==403)){
        String event=login?(status<400?"LOGIN_SUCCESS":"LOGIN_FAILED"):(status==401?"UNAUTHENTICATED":"REQUEST_DENIED");
        recorder.record((Long)request.getAttribute("auditUserId"),(String)request.getAttribute("auditAccount"),event,status<400,id,
          java.util.Objects.toString(request.getAttribute("errorCode"), "OK"),request.getMethod(),path,status);
      }
    }
  }
}
