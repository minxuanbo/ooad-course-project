package com.municipal.rbac;

import java.util.*;


/** Effective management access is a role permission bound to that user-role scope. */
@org.springframework.stereotype.Service
class ManagementAccess {
  static final Map<String,String> PERMISSIONS = Collections.unmodifiableMap(new LinkedHashMap<>(Map.ofEntries(
    Map.entry("user.read","人员查询"), Map.entry("user.roles","用户角色分配"),
    Map.entry("role.read","角色查看"), Map.entry("role.permissions","角色权限配置"),
    Map.entry("org.read","组织查看"), Map.entry("org.write","组织维护"),
    Map.entry("app.read","系统目录查看"), Map.entry("audit.read","审计日志查看"),
    Map.entry("handover.start","离职发起"), Map.entry("handover.complete","离职完成"),
    Map.entry("management.grant","管理授权配置"))));
  static final Set<String> PLATFORM_ONLY=Set.of("audit.read","role.permissions","management.grant");
  private final ManagementAccessRepository repository;
  ManagementAccess(ManagementAccessRepository repository){this.repository=repository;}
  List<Map<String,Object>> grants(long uid){
    return repository.effectiveGrants(uid);
  }
  boolean has(long uid,String permission){return repository.permissionCount(uid,permission)>0;}
  boolean global(long uid,String permission){return repository.globalPermissionCount(uid,permission)>0;}
  void require(long uid,String permission){if(!has(uid,permission))throw ApiException.forbidden();}
  void requireGlobal(long uid,String permission){if(!global(uid,permission))throw ApiException.forbidden();}
  String scope(long uid,String permission,String department){
    if(!PERMISSIONS.containsKey(permission))throw new IllegalArgumentException("Unknown management permission");
    return repository.scope(uid,permission,department);
  }
  boolean company(long uid,String permission,long company){return global(uid,permission)||repository.companyPermissionCount(uid,permission,company)>0;}
  void user(long uid,String permission,long target){
    require(uid,permission);
    if(global(uid,permission))return;
    if(repository.scopedUserCount(scope(uid,permission,"u.department_id"),target)==0)throw ApiException.scopeDenied();
  }
  void department(long uid,String permission,long target){
    if(repository.scopedDepartmentCount(scope(uid,permission,"d.id"),target)==0)throw ApiException.scopeDenied();
  }
  void lock(){repository.lockAuthorization();}
  void protectTarget(long actor,String permission,long target){
    user(actor,permission,target);
    if(!global(actor,permission) && (actor==target || !grants(target).isEmpty()))throw ApiException.forbidden();
  }
  void replaceRoles(long uid,Collection<Long> roles,long actor){
    for(Long old:repository.roleIds(uid))if(!roles.contains(old))repository.deleteRole(uid,old);
    for(Long role:new LinkedHashSet<>(roles))repository.insertRole(uid,actor,role);
  }
  void retainPlatformAdmin(){
    if(repository.platformAdminCount()==0)
      throw ApiException.bad("LAST_PLATFORM_ADMIN","必须保留至少一名可配置管理授权的平台管理员");
  }
}
