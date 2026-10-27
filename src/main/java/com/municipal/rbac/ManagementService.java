package com.municipal.rbac;

import java.util.*; import com.municipal.rbac.Requests.*;

import org.springframework.transaction.annotation.Transactional;


 
@org.springframework.stereotype.Service
class ManagementService {
  private final ManagementRepository repository;
  private final AuthService auth;
  private final ManagementAccess access;
  ManagementService(ManagementRepository repository,AuthService auth){this.repository=repository;this.auth=auth;this.access=auth.management();}




  private long operator(String header){return (long)auth.require(header).get("id");}
   public Map<String,String> catalog(String h){
    access.requireGlobal(operator(h),"role.permissions");return ManagementAccess.PERMISSIONS;
  }
   public List<String> rolePermissions(String h,long id){
    access.require(operator(h),"role.read");return repository.managementPermissions(id);
  }
   @Transactional
  public void rolePermissions(String h,long id,Permissions body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"role.permissions");
    String code=roleCode(id);
    if(body.permissions()==null || !ManagementAccess.PERMISSIONS.keySet().containsAll(body.permissions()))throw ApiException.bad("管理权限列表无效");
    if(!code.equals("系统管理员") && body.permissions().stream().anyMatch(ManagementAccess.PLATFORM_ONLY::contains))throw ApiException.bad("PLATFORM_ROLE_REQUIRED","日志查看与平台授权配置仅限系统管理员角色");
    repository.deleteManagementPermissions(id);
    for(String permission:new LinkedHashSet<>(body.permissions()))repository.insertManagementPermission(id,permission);
    access.retainPlatformAdmin();audit(actor,"ROLE",id,"SET_ROLE_MANAGEMENT_PERMISSIONS",body.permissions().toString());
  }
   public Responses.Assignments assignments(String h,long id){
    access.requireGlobal(operator(h),"management.grant");
    return new Responses.Assignments(DtoMapper.list(repository.assignmentRoles(id),Responses.Role.class),DtoMapper.list(repository.assignmentScopes(id),Requests.Scope.class));
  }
   @Transactional
  public void assign(String h,long id,Assignments body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"management.grant");
    if(body.roleIds()==null || body.scopes()==null || body.scopes().size()>200)throw ApiException.bad("角色与范围列表必填，范围最多 200 条");
    if(repository.activeUserCount(id)==0)throw ApiException.bad("USER_INACTIVE","只能为启用用户分配角色与范围");
    for(Long role:body.roleIds())roleCode(role);
    for(Scope scope:body.scopes())validate(scope,body.roleIds());
    access.replaceRoles(id,body.roleIds(),actor);
    repository.deleteScopes(id);
    for(Scope g:new LinkedHashSet<>(body.scopes()))repository.insertScope(id,g.roleId(),g.scope(),g.scope().equals("COMPANY")?g.companyId():null,g.departmentId());
    access.retainPlatformAdmin();audit(actor,"USER",id,"SET_ROLE_ASSIGNMENTS",body.toString());
  }
  private String roleCode(Long id){
    var roles=repository.activeRoleCode(id);
    if(roles.isEmpty())throw ApiException.bad("ROLE_UNAVAILABLE","角色不存在或已停用");return roles.get(0);
  }
  private void validate(Scope g,List<Long> roles){
    if(g==null || g.roleId()==null || !roles.contains(g.roleId()))throw ApiException.bad("SCOPE_ROLE_REQUIRED","范围必须绑定已选择的角色");
    String code=roleCode(g.roleId());
    if(!Set.of("GLOBAL","COMPANY","DEPARTMENT").contains(Objects.toString(g.scope(),"")))throw ApiException.bad("SCOPE_TYPE_INVALID","未知管理范围");
    if(code.equals("系统管理员") != g.scope().equals("GLOBAL"))throw ApiException.bad("SCOPE_ROLE_MISMATCH","系统管理员使用全平台范围，其他角色使用公司或部门范围");
    if(g.scope().equals("GLOBAL")){
      if(g.companyId()!=null || g.departmentId()!=null)throw ApiException.bad("SCOPE_ORGANIZATION_INVALID","平台范围不能指定组织");
    }else{
      if(g.companyId()==null || repository.activeCompanyCount(g.companyId())==0)throw ApiException.bad("COMPANY_UNAVAILABLE","公司不存在或已停用");
      if(g.scope().equals("COMPANY")){
        if(g.departmentId()!=null)throw ApiException.bad("SCOPE_ORGANIZATION_INVALID","公司范围不能指定部门");
      }else if(g.departmentId()==null || repository.activeDepartmentCount(g.departmentId(),g.companyId())==0)throw ApiException.bad("DEPARTMENT_UNAVAILABLE","部门不属于指定公司或已停用");
    }
  }
   public Responses.Organizations organizations(String h){
    long actor=operator(h);access.require(actor,"org.read");
    String scope=access.scope(actor,"org.read","d.id");
    return new Responses.Organizations(DtoMapper.list(repository.findCompanies(repository.companyScope(access.global(actor,"org.read"),scope)),Responses.Company.class),DtoMapper.list(repository.findDepartments(access.scope(actor,"org.write","d.id"),scope),Responses.Department.class));
  }
   @Transactional
  public void createCompany(String h,Organization body){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"org.write");validateOrganization(body);
    repository.insertCompany(body.code(),body.name());
    audit(actor,"COMPANY",repository.companyId(body.code()),"CREATE_COMPANY",body.name());
  }
   @Transactional
  public void renameCompany(String h,long id,OrganizationPatch patch){
    access.lock();long actor=operator(h);access.requireGlobal(actor,"org.write");var body=validatePatch(patch);
    if(repository.updateCompany(body.code(),body.name(),id)==0)throw ApiException.bad("COMPANY_NOT_FOUND","公司不存在");
    audit(actor,"COMPANY",id,"UPDATE_COMPANY",patch.fields().toString());
  }
   @Transactional
  public void createDepartment(String h,Organization body){
    access.lock();long actor=operator(h);access.require(actor,"org.write");validateOrganization(body);
    if(body.companyId()==null || repository.companyCount(body.companyId())==0)throw ApiException.bad("COMPANY_NOT_FOUND","公司不存在");
    if(body.parentId()!=null)throw ApiException.bad("不支持上级部门，请指定公司");
    // Only company/global maintainers may create departments: exact department grants cannot expand their scope.
    if(!access.company(actor,"org.write",body.companyId()))throw ApiException.forbidden();
    repository.insertDepartment(body.companyId(),body.code(),body.name());
    audit(actor,"DEPARTMENT",repository.departmentId(body.code()),"CREATE_DEPARTMENT",body.name());
  }
   @Transactional
  public void renameDepartment(String h,long id,OrganizationPatch patch){
    access.lock();long actor=operator(h);access.department(actor,"org.write",id);var body=validatePatch(patch);
    repository.updateDepartment(body.code(),body.name(),id);
    audit(actor,"DEPARTMENT",id,"UPDATE_DEPARTMENT",patch.fields().toString());
  }
  private Organization validatePatch(OrganizationPatch patch){
    var fields=patch.fields();
    if(fields.isEmpty())throw ApiException.bad("INVALID_ORGANIZATION_PATCH","请提供编码或名称");
    for(var entry:fields.entrySet()){
      String key=entry.getKey(),value=entry.getValue();
      if(!Set.of("code","name").contains(key))throw ApiException.bad("INVALID_ORGANIZATION_PATCH","仅允许修改编码和名称");
      if(value==null || value.isBlank() || value.length()>(key.equals("code")?100:191))throw ApiException.bad("INVALID_ORGANIZATION_PATCH","编码或名称无效");
    }
    return new Organization(fields.get("code"),fields.get("name"),null,null);
  }
  private void validateOrganization(Organization body){
    if(body.code()==null || body.code().isBlank() || body.code().length()>100 || body.name()==null || body.name().isBlank() || body.name().length()>191)throw ApiException.bad("请填写有效的组织编码与名称");
  }
  private void audit(long actor,String type,long target,String action,String detail){repository.recordChange(actor,type,target,action,detail);}
}
