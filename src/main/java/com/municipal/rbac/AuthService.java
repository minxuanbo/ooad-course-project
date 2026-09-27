package com.municipal.rbac;

import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
  private final JdbcTemplate db; private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(); private final int hours; private final SecureRandom random=new SecureRandom();
  AuthService(JdbcTemplate db,@Value("${rbac.session-hours:12}")int hours){this.db=db;this.hours=hours;}
  @Transactional Map<String,Object> login(String account,String password){var users=db.query("SELECT id,username,name,password_hash,status FROM users WHERE username=? OR employee_no=? OR phone=?",(rs,i)->Map.<String,Object>of("id",rs.getLong(1),"username",rs.getString(2),"name",rs.getString(3),"hash",rs.getString(4),"status",rs.getString(5)),account,account,account);if(users.size()!=1||!"ACTIVE".equals(users.get(0).get("status"))||!encoder.matches(password,(String)users.get(0).get("hash")))throw new ApiException(HttpStatus.UNAUTHORIZED,"账号或密码错误");byte[] b=new byte[32];random.nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),users.get(0).get("id"),Instant.now().plus(Duration.ofHours(hours)).toString());return Map.of("token",token,"user",Map.of("id",users.get(0).get("id"),"username",users.get(0).get("username"),"name",users.get(0).get("name")),"expiresIn",hours*3600);}
  Map<String,Object> require(String header){if(header==null||!header.startsWith("Bearer "))throw ApiException.unauthorized();String token=header.substring(7);var x=db.query("SELECT u.id,u.username,u.name FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.revoked=0 AND s.expires_at>? AND u.status='ACTIVE'",(rs,i)->Map.<String,Object>of("id",rs.getLong(1),"username",rs.getString(2),"name",rs.getString(3)),hash(token),Instant.now().toString());if(x.isEmpty())throw ApiException.unauthorized();return x.get(0);}
  void logout(String header){if(header!=null&&header.startsWith("Bearer "))db.update("UPDATE sessions SET revoked=1 WHERE token_hash=?",hash(header.substring(7)));}
  boolean has(long uid,String permission){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id AND r.enabled=1 JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id AND p.enabled=1 JOIN applications a ON a.id=p.app_id AND a.enabled=1 WHERE ur.user_id=? AND p.code=?)",Boolean.class,uid,permission));}
  boolean canManage(long uid){return db.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='系统管理员' AND r.enabled=1",Integer.class,uid)>0;}
  void requireAdmin(long uid){if(!canManage(uid))throw ApiException.forbidden();}
  private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
