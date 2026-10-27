package com.municipal.rbac;

import java.util.*; import com.municipal.rbac.Requests.*; import org.springframework.http.*;   import org.springframework.transaction.annotation.Transactional;

 
@org.springframework.stereotype.Service
class ApplicationService {
  private final ApplicationRepository repository; private final AuthService auth;
  ApplicationService(ApplicationRepository repository,AuthService auth){this.repository=repository;this.auth=auth;}

   public Responses.Login login(Login x){if(x.account()==null||x.password()==null||x.account().length()>100||x.password().length()>200)throw ApiException.bad("账号和密码不能为空");return auth.login(x.account().trim(),x.password());}
   public void logout(String h){auth.logout(h);}
   public Responses.Me me(String h){var u=auth.require(h);long id=(long)u.get("id");return new Responses.Me(DtoMapper.list(auth.management().grants(id),Responses.Grant.class),auth.canManage(id),DtoMapper.one(u,Responses.Identity.class),DtoMapper.list(repository.rolesOfUser(id),Responses.Role.class),repository.permissionCount(id));}
   public List<Responses.Permission> myPermissions(String h,String app){
    long id=(long)auth.require(h).get("id");
    return DtoMapper.list(repository.myPermissions(id,app),Responses.Permission.class);
  }
   public Responses.Decision check(String h,Check x){var u=auth.require(h);long id=(long)u.get("id");if(x.appCode()!=null&&x.appCode().length()>191||x.permissionCode()!=null&&x.permissionCode().length()>512||x.requestId()!=null&&x.requestId().length()>191)throw ApiException.bad("鉴权字段过长");boolean ok=x.permissionCode()!=null&&x.appCode()!=null&&auth.has(id,x.permissionCode())&&repository.permissionInApplication(x.permissionCode(),x.appCode())==1;repository.recordDecision(id,u.get("username"),Objects.toString(x.appCode(),""),Objects.toString(x.permissionCode(),""),ok?1:0,x.requestId(),ok?"角色权限命中":"未获任何角色授权");return new Responses.Decision(ok,Objects.toString(x.permissionCode(),""),Objects.toString(x.requestId(),""));}
   public Responses.Dashboard dashboard(String h){
    long uid=(long)auth.require(h).get("id");var mg=auth.management();mg.require(uid,"user.read");
    return new Responses.Dashboard(repository.countScopedUsers(mg.scope(uid,"user.read","u.department_id")),
      repository.countScopedDepartments(mg.scope(uid,"user.read","d.id")),
      mg.has(uid,"app.read")?scalar("applications"):0,mg.has(uid,"app.read")?scalar("permissions"):0,
      mg.has(uid,"role.read")?scalar("roles"):0,mg.global(uid,"audit.read")?scalar("authorization_logs"):0);
  }
  
  public List<Responses.User> users(String h,String q,
      String company,Long departmentId,
      int page,int size){
    long uid=(long)auth.require(h).get("id");auth.management().require(uid,"user.read");
    return userRows(uid,q,company,departmentId,Math.max(page,0),Math.min(Math.max(size,1),100));
  }
  
  public Responses.UserPage userPage(String h,String q,
      String company,Long departmentId,
      int page,int size){
    long uid=(long)auth.require(h).get("id");auth.management().require(uid,"user.read");
    page=Math.max(page,0);size=Math.min(Math.max(size,1),100);
    var args=new ArrayList<Object>();
    String where=repository.userWhere(q,company,departmentId,args,auth.management().scope(uid,"user.read","u.department_id"));
    long total=repository.countUsers(where,args.toArray());
    return new Responses.UserPage(userRows(uid,q,company,departmentId,page,size),total,page,size);
  }
  
  public Responses.Filters userFilters(String h,String company){
    long uid=(long)auth.require(h).get("id");auth.management().require(uid,"user.read");
    String scope=auth.management().scope(uid,"user.read","u.department_id");
    return new Responses.Filters(DtoMapper.list(repository.companyOptions(scope),Responses.CompanyOption.class),DtoMapper.list(repository.departmentOptions(scope,company,company),Responses.DepartmentOption.class));
  }
  private List<Responses.User> userRows(long uid,String q,String company,Long departmentId,int page,int size){
    var args=new ArrayList<Object>();
    String where=repository.userWhere(q,company,departmentId,args,auth.management().scope(uid,"user.read","u.department_id"));
    args.add(size);args.add((long)page*size);
    var result=repository.findUsers(where,args.toArray());
    for(var user:result){long target=((Number)user.get("id")).longValue();var actions=new ArrayList<String>();
      for(String permission:List.of("user.roles","handover.start","handover.complete")){
        try{auth.management().protectTarget(uid,permission,target);actions.add(permission);}catch(ApiException ignored){}
      }
      user.put("managementActions",actions);
    }
    return DtoMapper.list(result,Responses.User.class);
  }
   public List<Responses.Role> userRoles(String h,long id){auth.management().protectTarget((long)auth.require(h).get("id"),"user.roles",id);return DtoMapper.list(repository.roleOptions(id),Responses.Role.class);}
   @Transactional(rollbackFor=Exception.class) public void setUserRoles(String h,long id,Ids body){var op=auth.require(h);auth.management().lock();long uid=(long)op.get("id");auth.management().protectTarget(uid,"user.roles",id);if(body.ids()==null)throw ApiException.bad("角色列表不能为空");validateAssignableRoles(uid,id,body.ids());if(repository.lockUser(id).isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");if(repository.departedCount(id)>0)throw ApiException.bad("USER_DEPARTED","已离职员工不能再分配角色");auth.management().replaceRoles(id,body.ids(),uid);auth.management().retainPlatformAdmin();repository.recordChange(op.get("id"),"USER",id,"SET_ROLES",body.ids().toString());}
   public List<Responses.Role> roles(String h){long uid=(long)auth.require(h).get("id");auth.management().require(uid,"role.read");return DtoMapper.list(repository.findRoles(auth.management().scope(uid,"role.read","ru.department_id")),Responses.Role.class);}
   public List<Responses.Permission> rolePermissions(String h,long id,String q){auth.management().require((long)auth.require(h).get("id"),"role.read");String like="%"+q+"%";return DtoMapper.list(repository.permissionOptions(id,like,like,like),Responses.Permission.class);}
   @Transactional(rollbackFor=Exception.class) public void setRolePermissions(String h,long id,Ids body){var op=auth.require(h);auth.management().lock();auth.management().requireGlobal((long)op.get("id"),"role.permissions");if(body.ids()==null)throw ApiException.bad("权限列表不能为空");repository.deleteRolePermissions(id);for(Long pid:new LinkedHashSet<>(body.ids()))repository.insertRolePermission(id,pid);repository.recordRoleChange(op.get("id"),"ROLE",id,"SET_PERMISSIONS","count="+body.ids().size());}
   public List<Responses.Application> apps(String h){auth.management().require((long)auth.require(h).get("id"),"app.read");return DtoMapper.list(repository.findApplications(),Responses.Application.class);}
  
  public List<Responses.Application> myApplications(String h) {
    long uid=(long)auth.require(h).get("id");
    return DtoMapper.list(repository.accessibleApplications(uid),Responses.Application.class);
  }
  
  public Responses.Entry enterApplication(String h,long id) {
    var user=auth.require(h);var apps=repository.findApplication(id);
    if(apps.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"APPLICATION_NOT_FOUND","系统不存在");
    boolean allowed=auth.canEnter((long)user.get("id"),id);
    repository.recordApplicationEntry(user.get("id"),user.get("username"),apps.get(0).get("code"),"system:enter",allowed?1:0,allowed?"系统准入通过":"无权访问或授权已回收",RequestAuditFilter.requestId(),"/api/me/applications/"+id+"/enter",allowed?200:403);
    if(!allowed)throw new ApiException(HttpStatus.FORBIDDEN,"APPLICATION_ACCESS_DENIED","无权访问该系统或授权已被回收");
    return new Responses.Entry(true,DtoMapper.one(apps.get(0),Responses.Application.class),"modules");
  }
  
  public Responses.Modules modules(String h,long id){
    long uid=(long)auth.require(h).get("id");auth.requireApplication(uid,id);
    var modules=repository.findModules(uid,id);
    return new Responses.Modules(id,DtoMapper.list(modules,Responses.Module.class));
  }
  
  public Responses.Features features(String h,long id,long moduleId){
    long uid=(long)auth.require(h).get("id");auth.requireApplication(uid,id);
    if(repository.moduleInApplication(moduleId,id)==0)
      throw new ApiException(HttpStatus.NOT_FOUND,"MODULE_NOT_FOUND","模块不存在");
    var features=repository.findFeatures(moduleId);
    var operations=repository.findOperations(uid,moduleId);
    for(var feature:features){
      var list=new ArrayList<Map<String,Object>>();
      for(var operation:operations)if(Objects.equals(feature.get("id"),operation.get("featureId"))){
        var item=new LinkedHashMap<String,Object>(operation);item.remove("featureId");
        item.put("allowed",((Number)operation.get("allowed")).intValue()==1);list.add(item);
      }
      feature.put("operations",list);
    }
    return new Responses.Features(id,moduleId,DtoMapper.list(features,Responses.Feature.class));
  }
  
  public Responses.Handover handover(String h,long id){
    auth.management().user((long)auth.require(h).get("id"),"user.read",id);
    var user=repository.userIdentity(id);
    if(user.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");
    var records=repository.handoverDetail(id);
    long viewer=(long)auth.require(h).get("id");
    if(!records.isEmpty())for(String person:List.of("receiver","owner")){
      try{auth.management().user(viewer,"user.read",((Number)records.get(0).get(person+"_id")).longValue());}
      catch(ApiException ignored){records.get(0).put(person+"_name","范围外人员");records.get(0).put(person+"_no",null);records.get(0).put(person+"_id",null);}
    }
    return new Responses.Handover(DtoMapper.one(user.get(0),Responses.Identity.class),records.isEmpty()?null:DtoMapper.one(records.get(0),Responses.HandoverRecord.class),DtoMapper.list(repository.handoverRoles(id),Responses.Role.class),DtoMapper.list(repository.handoverSystems(id),Responses.SystemName.class),DtoMapper.list(repository.handoverHistory(id,auth.management().global(viewer,"management.grant")),Responses.Change.class));
  }
  
  @Transactional(rollbackFor=Exception.class)
  public void beginHandover(String h,long id,Handover body)throws Exception{
    var input=body.fields();
    auth.management().lock();long operator=(long)auth.require(h).get("id");auth.management().protectTarget(operator,"handover.start",id);
    if(id==operator)throw ApiException.bad("SELF_HANDOVER_DENIED","不能为当前登录账号办理离职，请由其他管理员办理");
    var user=repository.lockUserStatus(id);
    if(user.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");
    if(!"ACTIVE".equals(user.get(0).get("status")))throw ApiException.bad("USER_INACTIVE","该账号已停用，请先核实人员状态");
    if(repository.handoverCount(id)>0)throw ApiException.bad("HANDOVER_EXISTS","该员工已有离职交接记录，请勿重复发起");
    String reason=requiredText(input,"reason",1000),date=requiredText(input,"expectedDate",10);
    try{java.time.LocalDate.parse(date);}catch(java.time.format.DateTimeParseException e){throw ApiException.bad("预计完成日期格式应为 YYYY-MM-DD");}
    long receiver=handoverPerson(requiredText(input,"receiverNo",100),id);
    long owner=handoverPerson(requiredText(input,"ownerNo",100),id);
    auth.management().user(operator,"handover.start",receiver);auth.management().user(operator,"handover.start",owner);
    String oa=input.getOrDefault("oaReference","");if(oa==null)oa="";if(oa.length()>200)throw ApiException.bad("OA单号过长");
    repository.insertHandover(id,receiver,owner,date,reason,oa,operator);
    change(operator,id,"BEGIN_HANDOVER",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input));
  }
  
  @Transactional(rollbackFor=Exception.class)
  public void completeHandover(String h,long id,Completion body)throws Exception{
    var input=body.fields();
    auth.management().lock();long operator=(long)auth.require(h).get("id");auth.management().protectTarget(operator,"handover.complete",id);
    if(id==operator)throw ApiException.bad("SELF_HANDOVER_DENIED","不能停用当前登录账号，请由其他管理员办理");
    repository.lockDepartingUser(id);
    var records=repository.lockHandover(id);
    if(records.isEmpty())throw ApiException.bad("HANDOVER_NOT_STARTED","请先发起离职交接");
    if("DEPARTED".equals(records.get(0).get("state")))return;
    String note=requiredText(input,"note",1000);
    if(!"true".equals(input.get("confirmed")))throw ApiException.bad("请确认交接已完成，并确认集中撤权及停用账号");
    // 一个事务同时完成撤权、停用、撤销会话、更新交接状态与留痕。
    var before=repository.rolesBeforeDeparture(id);
    repository.removeUserRoles(id);
    auth.management().retainPlatformAdmin();
    repository.disableUser(id);
    repository.revokeSessions(id);
    repository.finishHandover(operator,note,id);
    change(operator,id,"COMPLETE_HANDOVER",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("revokedRoles",before,"note",note,"status","DISABLED")));
  }
  private void validateAssignableRoles(long uid,long target,List<Long> ids){
    if(auth.management().global(uid,"user.roles"))return;
    var roles=new LinkedHashSet<Long>(ids);
    roles.addAll(repository.existingRoleIds(target));
    for(Long role:roles)if(repository.permissionsOutsideActor(role,uid)>0)throw ApiException.forbidden();
  }
  private String requiredText(Map<String,String> input,String field,int max){
    String value=input.get(field);
    if(value==null||value.isBlank()||value.length()>max)throw ApiException.bad("请正确填写字段："+field);
    return value.trim();
  }
  private long handoverPerson(String employeeNo,long departingId){
    var people=repository.eligibleHandoverPerson(employeeNo);
    if(people.isEmpty())throw ApiException.bad("接收人或负责人必须是启用且未办理离职的员工："+employeeNo);
    long id=((Number)people.get(0).get("id")).longValue();
    if(id==departingId)throw ApiException.bad("交接人员不能是离职员工本人");
    return id;
  }
  private void change(long operator,long id,String action,String detail){
    repository.recordUserChange(operator,id,action,detail);
  }
  private int scalar(String table){return repository.scalar(table);}
}
