package com.municipal.rbac;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.municipal.rbac.Requests.*;
@RestController @RequestMapping("/api")
class ManagementController {
  private final ManagementService service;
  ManagementController(ManagementService service){this.service=service;}
  @GetMapping("/management/permissions")
  Map<String,String> catalog(@RequestHeader("Authorization")String h) { return service.catalog(h); }
  @GetMapping("/roles/{id}/management-permissions")
  List<String> rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id) { return service.rolePermissions(h,id); }
  @PutMapping("/roles/{id}/management-permissions")
  void rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Permissions body) { service.rolePermissions(h,id,body); }
  @GetMapping("/users/{id}/role-assignments")
  Responses.Assignments assignments(@RequestHeader("Authorization")String h,@PathVariable long id) { return service.assignments(h,id); }
  @PutMapping("/users/{id}/role-assignments")
  void assign(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Assignments body) { service.assign(h,id,body); }
  @GetMapping("/organizations")
  Responses.Organizations organizations(@RequestHeader("Authorization")String h) { return service.organizations(h); }
  @PostMapping("/organizations/companies")
  void createCompany(@RequestHeader("Authorization")String h,@RequestBody Organization body) { service.createCompany(h,body); }
  @PatchMapping("/organizations/companies/{id}")
  void renameCompany(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody OrganizationPatch body) { service.renameCompany(h,id,body); }
  @PostMapping("/organizations/departments")
  void createDepartment(@RequestHeader("Authorization")String h,@RequestBody Organization body) { service.createDepartment(h,body); }
  @PatchMapping("/organizations/departments/{id}")
  void renameDepartment(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody OrganizationPatch body) { service.renameDepartment(h,id,body); }
}
