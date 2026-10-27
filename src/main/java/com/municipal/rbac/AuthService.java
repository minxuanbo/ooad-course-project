package com.municipal.rbac;
import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service
class AuthService {
  private final AuthRepository repository;private final ManagementAccess access;private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();private final int hours;private final SecureRandom random=new SecureRandom();
  AuthService(AuthRepository repository,ManagementAccess access,@Value("${rbac.session-hours:12}")int hours){this.repository=repository;this.access=access;this.hours=hours;}
  @Transactional public Responses.Login login(String account,String password){
    RequestAuditFilter.actor(null,account);
    var users=repository.loginUser(account);
    if(!users.isEmpty())RequestAuditFilter.actor((Long)users.get(0).get("id"),account);
    if(users.size()!=1||!"ACTIVE".equals(users.get(0).get("status"))||!encoder.matches(password,(String)users.get(0).get("hash")))throw new ApiException(HttpStatus.UNAUTHORIZED,"LOGIN_FAILED","账号或密码错误");
    byte[] b=new byte[32];random.nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    repository.createSession(hash(token),users.get(0).get("id"),java.sql.Timestamp.from(Instant.now().plus(Duration.ofHours(hours))));
    return new Responses.Login(token,new Responses.Identity((Long)users.get(0).get("id"),(String)users.get(0).get("username"),(String)users.get(0).get("name"),"ACTIVE"),hours*3600);
  }
  Map<String,Object> require(String header){
    if(header==null||!header.startsWith("Bearer "))throw new ApiException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","请先登录");
    var users=repository.sessionUser(hash(header.substring(7)),java.sql.Timestamp.from(Instant.now()));
    if(users.isEmpty())throw ApiException.unauthorized();
    var user=users.get(0);RequestAuditFilter.actor((Long)user.get("id"),(String)user.get("username"));return user;
  }
  void logout(String header){if(header!=null&&header.startsWith("Bearer "))repository.revoke(hash(header.substring(7)));}
  boolean has(long uid,String permission){return repository.has(uid,permission);}
  boolean canEnter(long uid,long app){return repository.canEnter(uid,app);}
  void requireApplication(long uid,long app){if(!repository.applicationExists(app))throw new ApiException(HttpStatus.NOT_FOUND,"APPLICATION_NOT_FOUND","系统不存在");if(!canEnter(uid,app))throw new ApiException(HttpStatus.FORBIDDEN,"APPLICATION_ACCESS_DENIED","没有访问该系统的权限");}
  ManagementAccess management(){return access;}
  boolean canManage(long uid){return !access.grants(uid).isEmpty();}
  private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
