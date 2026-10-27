package com.municipal.rbac;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.municipal.rbac.Requests.*;
@RestController @RequestMapping("/api")
class ApiController {
  private final ApplicationService service;
  ApiController(ApplicationService service){this.service=service;}
  @PostMapping("/auth/login")
  Responses.Login login(@RequestBody Login x) { return service.login(x); }
  @PostMapping("/auth/logout")
  void logout(@RequestHeader(value="Authorization",required=false)String h) { service.logout(h); }
  @GetMapping("/me")
  Responses.Me me(@RequestHeader("Authorization")String h) { return service.me(h); }
  @GetMapping("/me/permissions")
  List<Responses.Permission> myPermissions(@RequestHeader("Authorization")String h,@RequestParam(required=false)String app) { return service.myPermissions(h,app); }
  @PostMapping("/authorize")
  Responses.Decision check(@RequestHeader("Authorization")String h,@RequestBody Check x) { return service.check(h,x); }
  @GetMapping("/dashboard")
  Responses.Dashboard dashboard(@RequestHeader("Authorization")String h) { return service.dashboard(h); }
  @GetMapping("/users")
  List<Responses.User> users(@RequestHeader("Authorization")String h,@RequestParam(defaultValue="")String q,
      @RequestParam(defaultValue="")String company,@RequestParam(required=false)Long departmentId,
      @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size) { return service.users(h,q,company,departmentId,page,size); }
  @GetMapping("/users/page")
  Responses.UserPage userPage(@RequestHeader("Authorization")String h,@RequestParam(defaultValue="")String q,
      @RequestParam(defaultValue="")String company,@RequestParam(required=false)Long departmentId,
      @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) { return service.userPage(h,q,company,departmentId,page,size); }
  @GetMapping("/users/filters")
  Responses.Filters userFilters(@RequestHeader("Authorization")String h,@RequestParam(defaultValue="")String company) { return service.userFilters(h,company); }
  @GetMapping("/users/{id}/roles")
  List<Responses.Role> userRoles(@RequestHeader("Authorization")String h,@PathVariable long id) { return service.userRoles(h,id); }
  @PutMapping("/users/{id}/roles")
  void setUserRoles(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Ids body) { service.setUserRoles(h,id,body); }
  @GetMapping("/roles")
  List<Responses.Role> roles(@RequestHeader("Authorization")String h) { return service.roles(h); }
  @GetMapping("/roles/{id}/permissions")
  List<Responses.Permission> rolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestParam(defaultValue="")String q) { return service.rolePermissions(h,id,q); }
  @PutMapping("/roles/{id}/permissions")
  void setRolePermissions(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Ids body) { service.setRolePermissions(h,id,body); }
  @GetMapping("/applications")
  List<Responses.Application> apps(@RequestHeader("Authorization")String h) { return service.apps(h); }
  @GetMapping("/me/applications")
  List<Responses.Application> myApplications(@RequestHeader("Authorization") String h) { return service.myApplications(h); }
  @PostMapping("/me/applications/{id}/enter")
  Responses.Entry enterApplication(@RequestHeader("Authorization") String h,@PathVariable long id) { return service.enterApplication(h,id); }
  @GetMapping("/me/applications/{id}/modules")
  Responses.Modules modules(@RequestHeader("Authorization")String h,@PathVariable long id) { return service.modules(h,id); }
  @GetMapping("/me/applications/{id}/modules/{moduleId}/features")
  Responses.Features features(@RequestHeader("Authorization")String h,@PathVariable long id,@PathVariable long moduleId) { return service.features(h,id,moduleId); }
  @GetMapping("/users/{id}/handover")
  Responses.Handover handover(@RequestHeader("Authorization")String h,@PathVariable long id) { return service.handover(h,id); }
  @PostMapping("/users/{id}/handover")
  void beginHandover(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Handover body) throws Exception { service.beginHandover(h,id,body); }
  @PostMapping("/users/{id}/handover/complete")
  void completeHandover(@RequestHeader("Authorization")String h,@PathVariable long id,@RequestBody Completion body) throws Exception { service.completeHandover(h,id,body); }
}
