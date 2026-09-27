package com.municipal.rbac;

import java.util.*; import org.springframework.http.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api")
class ApiController {
  private final JdbcTemplate db; private final AuthService auth;
  ApiController(JdbcTemplate db,AuthService auth){this.db=db;this.auth=auth;}
  record Login(String account,String password){} record Check(String appCode,String permissionCode,String requestId){} record Ids(List<Long> ids){}
  @PostMapping("/auth/login") Map<String,Object> login(@RequestBody Login x){if(x.account()==null||x.password()==null)throw ApiException.bad("账号和密码不能为空");return auth.login(x.account().trim(),x.password());}
  @PostMapping("/auth/logout") void logout(@RequestHeader(value="Authorization",required=false)String h){auth.logout(h);}
  @GetMapping("/me") Map<String,Object> me(@RequestHeader("Authorization")String h){var u=auth.require(h);long id=(long)u.get("id");return Map.of("canManage",auth.canManage(id),"user",u,"roles",rows("SELECT r.id,r.code,r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.id",id),"permissionCount",db.queryForObject("SELECT count(DISTINCT p.id) FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=?",Integer.class,id));}
  @GetMapping("/me/permissions") List<Map<String,Object>> myPermissions(@RequestHeader("Authorization")String h,@RequestParam(required=false)String app){long id=(long)auth.require(h).get("id");String sql="SELECT DISTINCT a.code appCode,a.name appName,p.code,p.module_name module,p.feature_name feature,p.action FROM permissions p JOIN applications a ON a.id=p.app_id JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND p.enabled=1 AND r.enabled=1";return app==null?rows(sql+" ORDER BY a.id,p.id",id):rows(sql+" AND a.code=? ORDER BY p.id",id,app);}
  @PostMapping("/authorize") Map<String,Object> check(@RequestHeader("Authorization")String h,@RequestBody Check x){var u=auth.require(h);long id=(long)u.get("id");boolean ok=x.permissionCode()!=null&&x.appCode()!=null&&auth.has(id,x.permissionCode())&&db.queryForObject("SELECT count(*) FROM permissions p JOIN applications a ON a.id=p.app_id WHERE p.code=? AND a.code=?",Integer.class,x.permissionCode(),x.appCode())==1;db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,request_id,reason) VALUES(?,?,?,?,?,?,?)",id,u.get("username"),Objects.toString(x.appCode(),""),Objects.toString(x.permissionCode(),""),ok?1:0,x.requestId(),ok?"角色权限命中":"未获任何角色授权");return Map.of("allowed",ok,"permissionCode",Objects.toString(x.permissionCode(),""),"requestId",Objects.toString(x.requestId(),""));}
  @GetMapping("/dashboard") Map<String,Object> dashboard(@RequestHeader("Authorization")String h){auth.requireAdmin((long)auth.require(h).get("id"));return Map.of("users",scalar("users"),"departments",scalar("departments"),"applications",scalar("applications"),"permissions",scalar("permissions"),"roles",scalar("roles"),"authLogs",scalar("authorization_logs"));}
  @GetMapping("/users") List<Map<String,Object>> users(@RequestHeader("Authorization")String h,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size){auth.requireAdmin((long)auth.require(h).get("id"));size=Math.min(Math.max(size,1),100);String like="%"+q.trim()+"%";return rows("SELECT u.id,u.employee_no employeeNo,u.username,u.name,u.phone,u.company,d.name department,u.position,u.status,COALESCE((SELECT state FROM handovers WHERE user_id=u.id),'EMPLOYED') employmentStatus,group_concat(r.name, '、') roles FROM users u LEFT JOIN departments d ON d.id=u.department_id LEFT JOIN user_roles ur ON ur.user_id=u.id LEFT JOIN roles r ON r.id=ur.role_id WHERE u.name LIKE ? OR u.username LIKE ? OR u.phone LIKE ? GROUP BY u.id ORDER BY u.id LIMIT ? OFFSET ?",like,like,like,size,page*size);}
  @GetMapping("/users/{id}/roles") List<Map<String,Object>> userRoles(@RequestHeader("Authorization")String h,@PathVariable long id){auth.requireAdmin((long)auth.require(h).get("id"));return rows("SELECT r.id,r.code,r.name,CASE WHEN ur.user_id IS NULL THEN 0 ELSE 1 END assigned FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id AND ur.user_id=? ORDER BY r.id",id);}
  @PutMapping("/users/{id}/roles") @Transactional void setUserRoles(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Ids body){var op=auth.require(h);auth.requireAdmin((long)op.get("id"));if(body.ids()==null)throw ApiException.bad("角色列表不能为空");if(db.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,id)==0)throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");if(db.queryForObject("SELECT count(*) FROM handovers WHERE user_id=? AND state=\'DEPARTED\'",Integer.class,id)>0)throw ApiException.bad("已离职员工不能再分配角色");db.update("DELETE FROM user_roles WHERE user_id=?",id);for(Long rid:new LinkedHashSet<>(body.ids()))db.update("INSERT INTO user_roles(user_id,role_id,granted_by) SELECT ?,id,? FROM roles WHERE id=?",id,op.get("id"),rid);db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",op.get("id"),"USER",id,"SET_ROLES",body.ids().toString());}
  @GetMapping("/roles") List<Map<String,Object>> roles(@RequestHeader("Authorization")String h){auth.requireAdmin((long)auth.require(h).get("id"));return rows("SELECT r.id,r.code,r.name,r.description,r.enabled,count(DISTINCT ur.user_id) userCount,count(DISTINCT rp.permission_id) permissionCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id LEFT JOIN role_permissions rp ON rp.role_id=r.id GROUP BY r.id ORDER BY r.id");}
  @GetMapping("/roles/{id}/permissions") List<Map<String,Object>> rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestParam(defaultValue="")String q){auth.requireAdmin((long)auth.require(h).get("id"));String like="%"+q+"%";return rows("SELECT p.id,a.name appName,p.module_name module,p.feature_name feature,p.action,p.code,CASE WHEN rp.role_id IS NULL THEN 0 ELSE 1 END assigned FROM permissions p JOIN applications a ON a.id=p.app_id LEFT JOIN role_permissions rp ON rp.permission_id=p.id AND rp.role_id=? WHERE p.feature_name LIKE ? OR p.module_name LIKE ? OR a.name LIKE ? ORDER BY a.id,p.id",id,like,like,like);}
  @PutMapping("/roles/{id}/permissions") @Transactional void setRolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Ids body){var op=auth.require(h);auth.requireAdmin((long)op.get("id"));if(body.ids()==null)throw ApiException.bad("权限列表不能为空");db.update("DELETE FROM role_permissions WHERE role_id=?",id);for(Long pid:new LinkedHashSet<>(body.ids()))db.update("INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions WHERE id=?",id,pid);db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,?,?,?,?)",op.get("id"),"ROLE",id,"SET_PERMISSIONS","count="+body.ids().size());}
  @GetMapping("/applications") List<Map<String,Object>> apps(@RequestHeader("Authorization")String h){auth.requireAdmin((long)auth.require(h).get("id"));return rows("SELECT a.id,a.code,a.name,a.enabled,count(p.id) permissionCount FROM applications a LEFT JOIN permissions p ON p.app_id=a.id GROUP BY a.id ORDER BY a.id");}
  @GetMapping("/authorization-logs") List<Map<String,Object>> logs(@RequestHeader("Authorization")String h,@RequestParam(defaultValue="100")int limit){long uid=(long)auth.require(h).get("id");auth.requireAdmin(uid);return rows("SELECT id,occurred_at occurredAt,username,app_code appCode,permission_code permissionCode,allowed,request_id requestId,reason FROM authorization_logs ORDER BY id DESC LIMIT ?",Math.min(limit,500));}
  // 系统准入：当前有效角色至少授予该系统一个有效功能权限。
  @GetMapping("/me/applications")
  List<Map<String,Object>> myApplications(@RequestHeader("Authorization") String h) {
    long uid=(long)auth.require(h).get("id");
    return rows("SELECT a.id,a.code,a.name,a.enabled,0 connected,"+
      "(SELECT count(DISTINCT p.id) FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id "+
      "JOIN user_roles ur ON ur.role_id=rp.role_id JOIN roles r ON r.id=ur.role_id "+
      "WHERE ur.user_id=? AND p.app_id=a.id AND p.enabled=1 AND r.enabled=1 AND a.enabled=1) permissionCount "+
      "FROM applications a ORDER BY a.id",uid);
  }
  @PostMapping("/me/applications/{id}/enter")
  ResponseEntity<Map<String,Object>> enterApplication(@RequestHeader("Authorization") String h,@PathVariable long id) {
    var user=auth.require(h);
    var applications=rows("SELECT code,name FROM applications WHERE id=?",id);
    if(applications.isEmpty())return ResponseEntity.status(404).body(Map.of("error","系统不存在"));
    boolean allowed=db.queryForObject("SELECT count(*) FROM permissions p JOIN applications a ON a.id=p.app_id "+
      "JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id "+
      "JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND a.id=? AND a.enabled=1 AND p.enabled=1 AND r.enabled=1",
      Integer.class,user.get("id"),id)>0;
    String message=allowed?"您有权限访问该系统，但系统暂未接入。":"您当前无权限访问该系统，权限可能已被回收或系统已停用。";
    db.update("INSERT INTO authorization_logs(user_id,username,app_code,permission_code,allowed,reason) VALUES(?,?,?,?,?,?)",
      user.get("id"),user.get("username"),applications.get(0).get("code"),"system:enter",allowed?1:0,message);
    return ResponseEntity.status(allowed?200:403).body(Map.of("allowed",allowed,"connected",false,"message",message,"error",message));
  }
  @GetMapping("/users/{id}/handover")
  Map<String,Object> handover(@RequestHeader("Authorization")String h,@PathVariable long id){
    auth.requireAdmin((long)auth.require(h).get("id"));
    var user=rows("SELECT id,name,username,status FROM users WHERE id=?",id);
    if(user.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");
    var records=rows("SELECT h.*,receiver.name receiver_name,receiver.employee_no receiver_no,owner.name owner_name,owner.employee_no owner_no "+
      "FROM handovers h JOIN users receiver ON receiver.id=h.receiver_id JOIN users owner ON owner.id=h.owner_id WHERE h.user_id=?",id);
    return Map.of("user",user.get(0),"handover",records.isEmpty()?Map.of():records.get(0),
      "roles",rows("SELECT r.id,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?",id),
      "systems",rows("SELECT DISTINCT a.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN role_permissions rp ON rp.role_id=r.id "+
        "JOIN permissions p ON p.id=rp.permission_id JOIN applications a ON a.id=p.app_id WHERE ur.user_id=? AND r.enabled=1 AND p.enabled=1 AND a.enabled=1",id),
      "history",rows("SELECT occurred_at,operator_id,action,detail FROM authorization_changes WHERE target_type='USER' AND target_id=? ORDER BY id DESC LIMIT 50",id));
  }
  @PostMapping("/users/{id}/handover")
  @Transactional
  void beginHandover(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Map<String,String> input)throws Exception{
    long operator=(long)auth.require(h).get("id");auth.requireAdmin(operator);
    if(id==operator)throw ApiException.bad("不能为当前登录账号办理离职，请由其他管理员办理");
    var user=rows("SELECT id,status FROM users WHERE id=?",id);
    if(user.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"用户不存在");
    if(!"ACTIVE".equals(user.get(0).get("status")))throw ApiException.bad("该账号已停用，请先核实人员状态");
    if(db.queryForObject("SELECT count(*) FROM handovers WHERE user_id=?",Integer.class,id)>0)throw ApiException.bad("该员工已有离职交接记录，请勿重复发起");
    String reason=requiredText(input,"reason",1000),date=requiredText(input,"expectedDate",10);
    try{java.time.LocalDate.parse(date);}catch(java.time.format.DateTimeParseException e){throw ApiException.bad("预计完成日期格式应为 YYYY-MM-DD");}
    long receiver=handoverPerson(requiredText(input,"receiverNo",100),id);
    long owner=handoverPerson(requiredText(input,"ownerNo",100),id);
    String oa=input.getOrDefault("oaReference","");if(oa==null)oa="";if(oa.length()>200)throw ApiException.bad("OA单号过长");
    db.update("INSERT INTO handovers(user_id,receiver_id,owner_id,expected_date,reason,oa_reference,state,started_by) VALUES(?,?,?,?,?,?,'HANDOVER',?)",
      id,receiver,owner,date,reason,oa,operator);
    change(operator,id,"BEGIN_HANDOVER",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input));
  }
  @PostMapping("/users/{id}/handover/complete")
  @Transactional
  void completeHandover(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Map<String,String> input)throws Exception{
    long operator=(long)auth.require(h).get("id");auth.requireAdmin(operator);
    if(id==operator)throw ApiException.bad("不能停用当前登录账号，请由其他管理员办理");
    var records=rows("SELECT state FROM handovers WHERE user_id=?",id);
    if(records.isEmpty())throw ApiException.bad("请先发起离职交接");
    if("DEPARTED".equals(records.get(0).get("state")))return;
    String note=requiredText(input,"note",1000);
    if(!"true".equals(input.get("confirmed")))throw ApiException.bad("请确认交接已完成，并确认集中撤权及停用账号");
    // 一个事务同时完成撤权、停用、撤销会话、更新交接状态与留痕。
    var before=rows("SELECT r.id,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=?",id);
    db.update("DELETE FROM user_roles WHERE user_id=?",id);
    db.update("UPDATE users SET status='DISABLED' WHERE id=?",id);
    db.update("UPDATE sessions SET revoked=1 WHERE user_id=?",id);
    db.update("UPDATE handovers SET state='DEPARTED',completed_by=?,completed_at=CURRENT_TIMESTAMP,completion_note=? WHERE user_id=?",operator,note,id);
    change(operator,id,"COMPLETE_HANDOVER",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("revokedRoles",before,"note",note,"status","DISABLED")));
  }
  private String requiredText(Map<String,String> input,String field,int max){
    String value=input.get(field);
    if(value==null||value.isBlank()||value.length()>max)throw ApiException.bad("请正确填写字段："+field);
    return value.trim();
  }
  private long handoverPerson(String employeeNo,long departingId){
    var people=rows("SELECT u.id FROM users u LEFT JOIN handovers h ON h.user_id=u.id WHERE u.employee_no=? AND u.status='ACTIVE' AND h.user_id IS NULL",employeeNo);
    if(people.isEmpty())throw ApiException.bad("接收人或负责人必须是启用且未办理离职的员工："+employeeNo);
    long id=((Number)people.get(0).get("id")).longValue();
    if(id==departingId)throw ApiException.bad("交接人员不能是离职员工本人");
    return id;
  }
  private void change(long operator,long id,String action,String detail){
    db.update("INSERT INTO authorization_changes(operator_id,target_type,target_id,action,detail) VALUES(?,'USER',?,?,?)",operator,id,action,detail);
  }
  private int scalar(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
  private List<Map<String,Object>> rows(String sql,Object...args){return db.query(sql,(rs,n)->{var m=new LinkedHashMap<String,Object>();var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++)m.put(md.getColumnLabel(i),rs.getObject(i));return m;},args);}
}
