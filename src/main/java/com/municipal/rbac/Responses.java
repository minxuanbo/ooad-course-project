package com.municipal.rbac;
import java.util.*;
public interface Responses {
  record Identity(long id,String username,String name,String status){}
  record Role(long id,String code,String name,String description,Integer enabled,Long userCount,Long permissionCount,Integer assigned,Integer managementPermissionCount){}
  record Grant(long id,long roleId,String roleName,String permission,String scope,Long companyId,Long departmentId,String company,String department){}
  record User(long id,String employeeNo,String username,String name,String phone,String company,Long departmentId,String department,String position,String status,String employmentStatus,String roles,List<String> managementActions){}
  record Permission(Long id,String appCode,String appName,String code,String module,String feature,String action,Integer assigned){}
  record Application(long id,String code,String name,Integer enabled,Long permissionCount){}
  record Module(long id,String name,long featureCount,long permissionCount,long grantedPermissionCount){}
  record Operation(long permissionId,String permissionCode,String actionCode,String label,boolean allowed){}
  record Feature(long id,String name,String description,List<Operation> operations){}
  record Company(long id,String code,String name,int enabled){}
  record Department(long id,String code,String name,long companyId,int enabled,boolean writable){}
  record CompanyOption(String company){}
  record DepartmentOption(long id,String name){}
  record SystemName(String name){}
  record Change(String occurred_at,Long operator_id,String action,String detail){}
  record HandoverRecord(Long user_id,Long receiver_id,Long owner_id,String expected_date,String reason,String oa_reference,String state,Long started_by,String started_at,Long completed_by,String completed_at,String completion_note,String receiver_name,String receiver_no,String owner_name,String owner_no){}

  record Login(String token,Identity user,int expiresIn){}
  record Me(List<Grant> managementGrants,boolean canManage,Identity user,List<Role> roles,int permissionCount){}
  record Decision(boolean allowed,String permissionCode,String requestId){}
  record Dashboard(int users,int departments,int applications,int permissions,int roles,int authLogs){}
  record UserPage(List<User> items,long total,int page,int size){}
  record Filters(List<CompanyOption> companies,List<DepartmentOption> departments){}
  record Entry(boolean allowed,Application application,String next){}
  record Modules(long applicationId,List<Module> modules){}
  record Features(long applicationId,long moduleId,List<Feature> features){}
  record Handover(Identity user,HandoverRecord handover,List<Role> roles,List<SystemName> systems,List<Change> history){}
  record Assignments(List<Role> roles,List<Requests.Scope> scopes){}
  record Organizations(List<Company> companies,List<Department> departments){}
}
