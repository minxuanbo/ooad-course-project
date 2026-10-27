package com.municipal.rbac;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
class AuditRecorder {
  private final AuditRepository repository;
  AuditRecorder(AuditRepository repository){this.repository=repository;}
  @Transactional(propagation=Propagation.REQUIRES_NEW)
  public void record(Long user,String account,String event,boolean allowed,String request,String reason,String method,String path,int status){
    repository.record(user,account,event,allowed,request,reason,method,path,status);
  }
}
