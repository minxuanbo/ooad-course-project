package com.municipal.rbac;
import java.util.List; import java.util.Map;
public interface Requests {
  record Handover(String receiverNo,String ownerNo,String expectedDate,String reason,String oaReference) {
    Map<String,String> fields(){var fields=new java.util.LinkedHashMap<String,String>();fields.put("receiverNo",receiverNo);fields.put("ownerNo",ownerNo);fields.put("expectedDate",expectedDate);fields.put("reason",reason);fields.put("oaReference",oaReference);return fields;}
  }
  record Completion(String note,String confirmed) { Map<String,String> fields(){var fields=new java.util.LinkedHashMap<String,String>();fields.put("note",note);fields.put("confirmed",confirmed);return fields;} }


  public record Login(String account,String password){}
  public record Check(String appCode,String permissionCode,String requestId){}
  public record Ids(List<Long> ids){}
  public record Scope(Long roleId,String scope,Long companyId,Long departmentId){}
  public record Assignments(List<Long> roleIds,List<Scope> scopes){}
  public record Permissions(List<String> permissions){}
  public record OrganizationPatch(Map<String,String> fields){
    @com.fasterxml.jackson.annotation.JsonCreator(mode=com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public OrganizationPatch {}
  }
  public record Organization(String code,String name,Long companyId,Long parentId){}
}
