package com.municipal.rbac;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
  private final AuditRepository repository;private final AuthService auth;
  AuditService(AuditRepository repository,AuthService auth){this.repository=repository;this.auth=auth;}
  AuditDtos.Page list(String header,AuditDtos.Query input){
    long actor=(long)auth.require(header).get("id");auth.management().requireGlobal(actor,"audit.read");
    if(input.username()!=null&&input.username().length()>100 || input.appCode()!=null&&input.appCode().length()>191 || input.event()!=null&&input.event().length()>32 || input.cursor()!=null&&input.cursor().length()>4096)throw ApiException.bad("筛选字段过长");
    int limit=input.limit()==null?100:input.limit();if(limit<1||limit>500)throw ApiException.bad("limit 必须为 1–500");
    String storage=input.storage()==null?"ALL":input.storage();if(!Set.of("ALL","ACTIVE","ARCHIVED").contains(storage))throw ApiException.bad("未知归档状态");
    String from=time(input.from()),to=time(input.to());if(from!=null&&to!=null&&from.compareTo(to)>=0)throw ApiException.bad("开始时间必须早于结束时间");
    if(input.userId()!=null&&input.userId()<1)throw ApiException.bad("用户 ID 必须为正整数");
    var q=new AuditDtos.Query(input.userId(),input.username(),input.appCode(),input.allowed(),from,to,input.event(),storage,null,limit);
    String key=Base64.getUrlEncoder().withoutPadding().encodeToString(DtoMapper.MAPPER.valueToTree(q).toString().getBytes(StandardCharsets.UTF_8));
    long snapshot=repository.snapshot(),before=Long.MAX_VALUE;
    if(input.cursor()!=null){try{
      String[] parts=new String(Base64.getUrlDecoder().decode(input.cursor()),StandardCharsets.UTF_8).split(":",3);
      snapshot=Long.parseLong(parts[0]);before=Long.parseLong(parts[1]);if(snapshot<0||before<1||!parts[2].equals(key))throw new IllegalArgumentException();
    }catch(RuntimeException e){throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_CURSOR","游标无效或筛选条件已改变");}}
    var entries=repository.find(q,snapshot,before,limit+1);boolean more=entries.size()>limit;var items=List.copyOf(entries.subList(0,Math.min(limit,entries.size())));
    String next=more?Base64.getUrlEncoder().withoutPadding().encodeToString((snapshot+":"+items.get(items.size()-1).id()+":"+key).getBytes(StandardCharsets.UTF_8)):null;
    return new AuditDtos.Page(items,next,more,snapshot);
  }
  @Transactional public AuditDtos.ArchiveResult archive(String header,AuditDtos.Archive input){
    auth.management().lock();long actor=(long)auth.require(header).get("id");auth.management().requireGlobal(actor,"audit.read");
    String before=time(input.before());if(before==null||LocalDateTime.parse(before.replace(' ','T')).isAfter(LocalDateTime.now(ZoneOffset.UTC)))throw ApiException.bad("归档截止时间必须有效且不晚于当前时间");
    int batch=input.batchSize()==null?1000:input.batchSize();if(batch<1||batch>5000)throw ApiException.bad("归档批量必须为 1–5000");
    long snapshot=repository.snapshot();int count=repository.archive(actor,before,batch,snapshot);repository.archiveChange(actor,count,before);
    return new AuditDtos.ArchiveResult(count,repository.remaining(before,snapshot),snapshot);
  }
  private String time(String value){
    if(value==null)return null;
    try{return LocalDateTime.ofInstant(Instant.parse(value),ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));}catch(java.time.format.DateTimeParseException e){throw ApiException.bad("时间使用 UTC ISO-8601，例如 2026-10-27T00:00:00Z");}
  }
}
