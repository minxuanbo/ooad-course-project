package com.municipal.rbac;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.RequestContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RequestAuditBoundaryTest {
  @Test void backgroundCallsHaveNoRequestAndInternalErrorsNeverExposeSecrets(){
    var previous=RequestContextHolder.getRequestAttributes();RequestContextHolder.resetRequestAttributes();
    try{
      assertNull(RequestAuditFilter.current());assertEquals("",RequestAuditFilter.requestId());RequestAuditFilter.actor(1L,"background");RequestAuditFilter.error("test");
      var response=new ApiExceptionHandler().unexpected(new IllegalStateException("secret-token-and-sql"));
      assertEquals(500,response.getStatusCode().value());assertEquals("INTERNAL_ERROR",response.getBody().code());assertEquals("服务器内部错误",response.getBody().message());assertNull(response.getBody().data());
    }finally{RequestContextHolder.setRequestAttributes(previous);}
  }
  @Test void nonApiRequestsAreNotRecorded()throws Exception{
    var recorder=mock(AuditRecorder.class);var response=new MockHttpServletResponse();
    new RequestAuditFilter(recorder).doFilterInternal(new MockHttpServletRequest("GET","/favicon.ico"),response,(request,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(404));
    assertNotNull(response.getHeader("X-Request-Id"));verifyNoInteractions(recorder);
  }
}
