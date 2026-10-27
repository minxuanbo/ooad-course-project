package com.municipal.rbac;

import java.nio.file.*; import java.text.Normalizer; import java.util.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;

@Component @org.springframework.core.annotation.Order(10)
class DataInitializer implements ApplicationRunner {
  @Value("${rbac.initialize:true}") private boolean initialize;
  private final Map<Long,String> managerNames=new LinkedHashMap<>();
  private final JdbcTemplate db; private final Path source; private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
  DataInitializer(JdbcTemplate db,@Value("${rbac.source-dir:.}")String source){this.db=db;this.source=Path.of(source);}
  @Override @Transactional(rollbackFor=Exception.class) public void run(ApplicationArguments args)throws Exception { if(initialize && count("users")==0) seed(); }
  private int count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
  @Transactional(rollbackFor=Exception.class) void seed() throws Exception {
    seedDepartments(); Map<String,Long> roles=seedRoles(); long admin=insertUser("ADMIN000","admin","系统管理员",null,"总公司",null,"系统管理员",encoder.encode("admin123"));
    db.update("INSERT INTO user_roles(user_id,role_id,granted_by) VALUES(?,?,?)",admin,roles.get("系统管理员"),admin);
    seedEmployees(roles,admin); resolveManagers(); seedPermissions(roles); seedManagement(roles); System.out.println("RBAC 初始化完成：员工="+count("users")+"，部门="+count("departments")+"，权限="+count("permissions"));
  }
  private void seedManagement(Map<String,Long> roleIds){
    for(String role:List.of("系统管理员","公司领导","部门经理","项目经理")){
      var permissions=new LinkedHashSet<>(List.of("user.read","user.roles","role.read","org.read","app.read","handover.start","handover.complete"));
      if(role.equals("公司领导"))permissions.add("org.write");
      if(role.equals("系统管理员"))permissions.addAll(ManagementAccess.PERMISSIONS.keySet());
      for(String permission:permissions)db.update("INSERT IGNORE INTO role_management_permissions(role_id,permission_code) VALUES(?,?)",roleIds.get(role),permission);
    }
    db.update("INSERT INTO management_scopes(user_id,role_id,scope_type) SELECT ur.user_id,ur.role_id,'GLOBAL' FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE r.code='系统管理员' AND r.enabled=1 AND u.status='ACTIVE'");
    db.update("INSERT INTO management_scopes(user_id,role_id,scope_type,company_id,department_id) SELECT ur.user_id,ur.role_id,IF(r.code='公司领导','COMPANY','DEPARTMENT'),IF(r.code='公司领导',d.company_id,NULL),IF(r.code='公司领导',NULL,d.id) FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id JOIN departments d ON d.id=u.department_id WHERE r.code IN ('公司领导','部门经理','项目经理') AND r.enabled=1 AND u.status='ACTIVE'");
    new ManagementAccess(new ManagementAccessRepository(db)).retainPlatformAdmin();
  }
  private void seedDepartments()throws Exception {
    var employees=ExcelXmlReader.read(source.resolve("市政公司员工信息表.xlsx"));
    var companies=new LinkedHashMap<String,Long>();
    for(int i=1;i<employees.size();i++){
      String name=get(employees.get(i),2);if(name.isBlank()||companies.containsKey(name))continue;
      db.update("INSERT INTO companies(code,name) VALUES(?,?)","company-"+(companies.size()+1),name);
      companies.put(name,db.queryForObject("SELECT id FROM companies WHERE name=?",Long.class,name));
    }
    var rows=ExcelXmlReader.read(source.resolve("市政公司部门信息表.xlsx"));
    var ids=new LinkedHashMap<String,Long>();var owners=new LinkedHashMap<String,Long>();
    for(int i=1;i<rows.size();i++){
      String name=get(rows.get(i),0);if(name.isBlank())continue;
      String company="总公司";
      for(String candidate:companies.keySet())if(name.equals(candidate)||name.startsWith(candidate+"-"))company=candidate;
      if(!companies.containsKey(company))throw new IllegalStateException("部门公司归属未确认："+name);
      db.update("INSERT INTO departments(company_id,code,name) VALUES(?,?,?)",companies.get(company),"dept-"+i,name);
      long id=db.queryForObject("SELECT id FROM departments WHERE code=?",Long.class,"dept-"+i);
      ids.put(name,id);owners.put(name,companies.get(company));managerNames.put(id,get(rows.get(i),1));
    }
    for(int i=1;i<rows.size();i++){
      String name=get(rows.get(i),0),parent=get(rows.get(i),2);
      if(!parent.isBlank()&&!ids.containsKey(parent))throw new IllegalStateException("上级部门不存在");

    }
  }
  private void resolveManagers(){
    for(var entry:managerNames.entrySet()){
      var ids=db.queryForList("SELECT id FROM users WHERE department_id=? AND name=? AND status='ACTIVE'",Long.class,entry.getKey(),entry.getValue());
      if(ids.size()>1)throw new IllegalStateException("部门负责人重名，需要核对工号");
      if(ids.size()==1)db.update("UPDATE departments SET manager_user_id=? WHERE id=?",ids.get(0),entry.getKey());
    }
  }
  private Map<String,Long> seedRoles(){Map<String,Long> out=new LinkedHashMap<>();String[] names={"系统管理员","公司领导","部门经理","项目经理","普通员工"};for(String n:names){String c=safe(n);db.update("INSERT INTO roles(code,name,description) VALUES(?,?,?)",c,n,"由功能权限清单初始化");out.put(n,db.queryForObject("SELECT id FROM roles WHERE name=?",Long.class,n));}return out;}
  private void seedEmployees(Map<String,Long> roles,long admin)throws Exception {var rows=ExcelXmlReader.read(source.resolve("市政公司员工信息表.xlsx"));String hash=encoder.encode("123456");for(int i=1;i<rows.size();i++){var r=rows.get(i);String name=get(r,0),no=get(r,1),company=get(r,2),dept=get(r,3),position=get(r,4),phone=get(r,5);Long deptId=findDepartment(company,dept);long uid=insertUser(no,no,name,phone,company,deptId,position,hash);grant(uid,roles.get("普通员工"),admin);String primary=RolePolicy.primary(position,dept,isDepartmentManager(name,dept,company));grant(uid,roles.get(primary),admin);}}
  private void seedPermissions(Map<String,Long> roles)throws Exception {var rows=ExcelXmlReader.read(ExcelXmlReader.permissionFile(source));String app="",module="";for(int i=2;i<rows.size();i++){var r=rows.get(i);if(!get(r,0).isBlank())app=get(r,0);if(!get(r,1).isBlank())module=get(r,1);String feature=get(r,2);if(app.isBlank()||feature.isBlank())continue;long appId=ensureApp(app);long featureId=ensureFeature(appId,module,feature,get(r,3));Set<String> allActions=new LinkedHashSet<>();Map<String,Set<String>> byRole=new LinkedHashMap<>();for(int c=4;c<=8;c++){String role=switch(c){case 4->"系统管理员";case 5->"公司领导";case 6->"部门经理";case 7->"项目经理";default->"普通员工";};Set<String>a=actions(get(r,c));byRole.put(role,a);allActions.addAll(a);}if(byRole.get("系统管理员").contains("全部")){allActions.remove("全部");allActions.addAll(List.of("查看","新增","编辑","删除","审批","导出","配置"));}for(String action:allActions){if(action.equals("全部"))continue;String code=safe(app)+":"+safe(module)+":"+safe(feature)+":"+actionCode(action);ensureAction(action);db.update("INSERT INTO permissions(feature_id,action_code,code) VALUES(?,?,?)",featureId,actionCode(action),code);long pid=db.queryForObject("SELECT id FROM permissions WHERE code=?",Long.class,code);for(var e:byRole.entrySet())if(e.getValue().contains("全部")||e.getValue().contains(action))db.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?) ON DUPLICATE KEY UPDATE role_id=role_id",roles.get(e.getKey()),pid);}}}
  private long insertUser(String no,String username,String name,String phone,String company,Long dept,String position,String hash){db.update("INSERT INTO users(employee_no,username,name,phone,department_id,position,password_hash) VALUES(?,?,?,?,?,?,?)",no,username,name,phone==null||phone.isBlank()?null:phone,dept,position,hash);return db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,username);}
  private void grant(long u,long r,long by){db.update("INSERT INTO user_roles(user_id,role_id,granted_by) VALUES(?,?,?) ON DUPLICATE KEY UPDATE user_id=user_id",u,r,by);}
  private Long findDepartment(String company,String dept){
    for(String n:List.of(company+"-"+dept,dept,company)){
      var ids=db.queryForList("SELECT d.id FROM departments d JOIN companies c ON c.id=d.company_id WHERE c.name=? AND d.name=?",Long.class,company,n);
      if(ids.size()==1)return ids.get(0);
    }
    throw new IllegalStateException("员工部门未匹配，禁止丢弃公司归属");
  }
  private boolean isDepartmentManager(String name,String dept,String company){
    for(String n:List.of(dept,company+"-"+dept,company)){
      var ids=db.queryForList("SELECT d.id FROM departments d JOIN companies c ON c.id=d.company_id WHERE d.name=? AND c.name=?",Long.class,n,company);
      for(Long id:ids)if(name.equals(managerNames.get(id)))return true;
    }
    return false;
  }
  private long ensureFeature(long app,String module,String feature,String description){
    db.update("INSERT INTO modules(app_id,code,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=id",app,safe(module),module);
    long mid=db.queryForObject("SELECT id FROM modules WHERE app_id=? AND name=?",Long.class,app,module);
    db.update("INSERT INTO features(module_id,code,name,description) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=id",mid,safe(feature),feature,description);
    return db.queryForObject("SELECT id FROM features WHERE module_id=? AND name=?",Long.class,mid,feature);
  }
  private void ensureAction(String action){
    int order=List.of("查看","新增","编辑","删除","审批","导出","配置").indexOf(action);
    db.update("INSERT INTO actions(code,name,sort_order) VALUES(?,?,?) ON DUPLICATE KEY UPDATE code=code",actionCode(action),action,order<0?99:order);
  }
  private long ensureApp(String n){String c=safe(n);db.update("INSERT INTO applications(code,name) VALUES(?,?) ON DUPLICATE KEY UPDATE id=id",c,n);return db.queryForObject("SELECT id FROM applications WHERE code=?",Long.class,c);}
  private static Set<String> actions(String s){Set<String>r=new LinkedHashSet<>();if(s==null||s.isBlank()||s.equals("无"))return r;for(String x:s.split("[/、,，]"))if(!x.isBlank())r.add(x.trim());return r;}
  private static String get(List<String>r,int i){return i<r.size()&&r.get(i)!=null?r.get(i).trim():"";}
  private static String safe(String s){String n=Normalizer.normalize(s,Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]+","_").replaceAll("^_|_$","");return n.toLowerCase(Locale.ROOT);}
  private static String actionCode(String s){return Map.of("查看","read","新增","create","编辑","update","删除","delete","审批","approve","导出","export","配置","configure").getOrDefault(s,safe(s));}
}
