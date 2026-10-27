package com.municipal.rbac;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditRepository {
  private final JdbcTemplate db;
  AuditRepository(JdbcTemplate db){this.db=db;}
  void record(Long user,String account,String event,boolean allowed,String request,String reason,String method,String path,int status){
    db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,request_id,reason,event_type,http_method,request_path,http_status) VALUES(?,?,'','',?,?,?,?,?,?,?)",
      user,account==null?null:account.substring(0,Math.min(account.length(),100)),allowed?1:0,request,reason,event,method,path.substring(0,Math.min(path.length(),512)),status);
  }
  long snapshot(){return db.queryForObject("SELECT COALESCE(MAX(id),0) FROM authorization_logs",Long.class);}
  List<AuditDtos.Entry> find(AuditDtos.Query q,long snapshot,long before,int limit){
    StringBuilder sql=new StringBuilder("SELECT * FROM authorization_logs WHERE id<=? AND id<?");var args=new ArrayList<Object>(List.of(snapshot,before));
    if(q.userId()!=null){sql.append(" AND user_id=?");args.add(q.userId());}
    if(q.username()!=null){sql.append(" AND username=?");args.add(q.username());}
    if(q.appCode()!=null){sql.append(" AND app_code=?");args.add(q.appCode());}
    if(q.allowed()!=null){sql.append(" AND allowed=?");args.add(q.allowed());}
    if(q.from()!=null){sql.append(" AND occurred_at>=?");args.add(Timestamp.valueOf(q.from()));}
    if(q.to()!=null){sql.append(" AND occurred_at<?");args.add(Timestamp.valueOf(q.to()));}
    if(q.event()!=null){sql.append(" AND event_type=?");args.add(q.event());}
    if(q.storage().equals("ACTIVE"))sql.append(" AND archived_at IS NULL");
    if(q.storage().equals("ARCHIVED"))sql.append(" AND archived_at IS NOT NULL");
    sql.append(" ORDER BY id DESC LIMIT ?");args.add(limit);
    return db.query(sql.toString(),(r,n)->new AuditDtos.Entry(r.getLong("id"),r.getTimestamp("occurred_at").toLocalDateTime().toString(),r.getObject("user_id",Long.class),r.getString("username"),r.getString("app_code"),r.getString("permission_code"),r.getBoolean("allowed"),r.getString("request_id"),r.getString("reason"),r.getString("event_type"),r.getString("http_method"),r.getString("request_path"),r.getObject("http_status",Integer.class),r.getString("archived_at"),r.getObject("archived_by",Long.class)),args.toArray());
  }
  int archive(long actor,String before,int limit,long snapshot){return db.update("UPDATE authorization_logs SET archived_at=UTC_TIMESTAMP(3),archived_by=? WHERE archived_at IS NULL AND occurred_at<? AND id<=? ORDER BY id LIMIT ?",actor,Timestamp.valueOf(before),snapshot,limit);}
  boolean remaining(String before,long snapshot){return db.queryForObject("SELECT EXISTS(SELECT 1 FROM authorization_logs WHERE archived_at IS NULL AND occurred_at<? AND id<=?)",Boolean.class,Timestamp.valueOf(before),snapshot);}
  void archiveChange(long actor,int count,String before){db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,'AUDIT',0,'ARCHIVE_LOGS',?)",actor,"before="+before+", count="+count);}
}
