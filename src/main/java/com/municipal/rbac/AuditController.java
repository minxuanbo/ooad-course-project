package com.municipal.rbac;

import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/authorization-logs")
class AuditController {
  private final AuditService service;
  AuditController(AuditService service){this.service=service;}
  @GetMapping AuditDtos.Page list(@RequestHeader("Authorization")String header,@ModelAttribute AuditDtos.Query query){return service.list(header,query);}
  @PostMapping("/archive") AuditDtos.ArchiveResult archive(@RequestHeader("Authorization")String header,@RequestBody AuditDtos.Archive input){return service.archive(header,input);}
}
