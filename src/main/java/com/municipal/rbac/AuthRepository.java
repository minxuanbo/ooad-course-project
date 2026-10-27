package com.municipal.rbac;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
class AuthRepository {
  private final JdbcTemplate db;
  AuthRepository(JdbcTemplate db){this.db=db;}
  static final String CATALOG="permissions p JOIN features f ON f.id=p.feature_id JOIN modules m ON m.id=f.module_id JOIN applications a ON a.id=m.app_id JOIN actions ac ON ac.code=p.action_code";
  static final String ACTIVE="p.enabled=1 AND f.enabled=1 AND m.enabled=1 AND a.enabled=1 AND ac.enabled=1";
  static final String GRANTED="EXISTS(SELECT 1 FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN user_roles ur ON ur.role_id=r.id JOIN users gu ON gu.id=ur.user_id WHERE rp.permission_id=p.id AND r.enabled=1 AND gu.status='ACTIVE' AND ur.user_id=?)";

  List<Map<String,Object>> loginUser(String account){return db.query("SELECT u.id,u.username,u.name,u.password_hash,u.status FROM login_identifiers li JOIN users u ON u.id=li.user_id WHERE li.identifier=?",(r,i)->Map.<String,Object>of("id",r.getLong(1),"username",r.getString(2),"name",r.getString(3),"hash",r.getString(4),"status",r.getString(5)),account);}
  void createSession(String hash,Object id,Timestamp expires){db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",hash,id,expires);}
  List<Map<String,Object>> sessionUser(String hash,Timestamp now){return db.query("SELECT u.id,u.username,u.name FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.revoked=0 AND s.expires_at>? AND u.status='ACTIVE'",(r,i)->Map.<String,Object>of("id",r.getLong(1),"username",r.getString(2),"name",r.getString(3)),hash,now);}
  void revoke(String hash){db.update("UPDATE sessions SET revoked=1 WHERE token_hash=?",hash);}
  boolean has(long uid,String permission){return db.queryForObject("SELECT count(*) FROM "+CATALOG+" WHERE "+ACTIVE+" AND "+GRANTED+" AND p.code=?",Integer.class,uid,permission)>0;}
  boolean canEnter(long uid,long app){return db.queryForObject("SELECT count(*) FROM "+CATALOG+" WHERE "+ACTIVE+" AND "+GRANTED+" AND a.id=?",Integer.class,uid,app)>0;}
  boolean applicationExists(long app){return db.queryForObject("SELECT count(*) FROM applications WHERE id=?",Integer.class,app)>0;}
}
