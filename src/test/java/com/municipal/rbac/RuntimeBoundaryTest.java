package com.municipal.rbac;

import java.security.*;
import java.sql.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeBoundaryTest {
  @Test void mainDelegatesToSpringBoot(){
    try(var boot=mockStatic(SpringApplication.class)){
      String[] args={"--server.port=0"};RbacApplication.main(args);
      boot.verify(()->SpringApplication.run(RbacApplication.class,args));
    }
  }
  @Test void digestProviderFailureFailsClosed(){
    JdbcTemplate db=mock(JdbcTemplate.class);var auth=new AuthService(db,12);
    try(var digest=mockStatic(MessageDigest.class)){
      digest.when(()->MessageDigest.getInstance("SHA-256")).thenThrow(new NoSuchAlgorithmException("test unavailable provider"));
      var ex=assertThrows(IllegalStateException.class,()->auth.require("Bearer test"));
      assertInstanceOf(NoSuchAlgorithmException.class,ex.getCause());verifyNoInteractions(db);
    }
  }
  @Test void jdbcLocalDateTimeIsSerializedWithoutDriverSpecificTypeLeak()throws Exception{
    JdbcTemplate db=mock(JdbcTemplate.class);AuthService auth=mock(AuthService.class);
    when(auth.management()).thenReturn(mock(ManagementAccess.class));
    when(auth.require("Bearer test")).thenReturn(java.util.Map.of("id",1L));
    for(Object value:new Object[]{LocalDateTime.of(2026,10,23,12,0),java.sql.Timestamp.valueOf("2026-10-23 12:00:00"),java.sql.Date.valueOf("2026-10-23")}){
    when(db.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<java.util.Map<String,Object>>>any(),any(Object[].class))).thenAnswer(call->{
      RowMapper<?> mapper=call.getArgument(1);ResultSet rs=mock(ResultSet.class);ResultSetMetaData md=mock(ResultSetMetaData.class);
      when(rs.getMetaData()).thenReturn(md);when(md.getColumnCount()).thenReturn(1);when(md.getColumnLabel(1)).thenReturn("occurredAt");
      when(rs.getObject(1)).thenReturn(value);
      return java.util.List.of(mapper.mapRow(rs,0));
    });
    assertEquals(value instanceof java.sql.Date ? "2026-10-23" : "2026-10-23 12:00:00",new ApiController(db,auth).logs("Bearer test",1).get(0).get("occurredAt"));
    }
  }
}
