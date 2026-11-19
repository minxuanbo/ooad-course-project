package com.municipal.rbaccore.service.port.course;

import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 老师课程系统的协议取值及真实路由，不包含课程业务实现或授权计算。 */
public final class CourseContract {
  public static final String SYSTEM_CODE = "course-project-management";
  public static final String TEACHER = "COURSE_TEACHER";
  public static final String TEACHER_ROLE = "teacher";
  public static final Set<String> DISPLAY_CODES =
      Set.of(
          TEACHER, "MCO", "MTR", "MTE", "MLG", "MCOR", "MCOE", "MCOD", "MCOT", "MTRC", "MTRE",
          "MTRD", "MTRM", "MTEC", "MTEE", "MTED", "MTEL");
  public static final Map<String, List<RbacCatalog.Route>> BACKEND_ROUTES =
      Map.ofEntries(
          Map.entry("TC", List.of(new RbacCatalog.Route("POST", "/terms"))),
          Map.entry("TE", List.of(new RbacCatalog.Route("PATCH", "/terms/{termId}"))),
          Map.entry("TD", List.of(new RbacCatalog.Route("DELETE", "/terms/{termId}"))),
          Map.entry(
              "TCM",
              List.of(
                  new RbacCatalog.Route("PUT", "/current-term"),
                  new RbacCatalog.Route("DELETE", "/current-term"))),
          Map.entry(
              "CV",
              List.of(
                  new RbacCatalog.Route("GET", "/admin/courses"),
                  new RbacCatalog.Route("GET", "/courses/{courseId}"))),
          Map.entry("CR", List.of(new RbacCatalog.Route("POST", "/courses/{courseId}/review"))),
          Map.entry("CE", List.of(new RbacCatalog.Route("PATCH", "/courses/{courseId}"))),
          Map.entry("CD", List.of(new RbacCatalog.Route("DELETE", "/courses/{courseId}"))),
          Map.entry("CC", List.of(new RbacCatalog.Route("POST", "/courses/copy"))),
          Map.entry(
              "CTM",
              List.of(
                  new RbacCatalog.Route("PUT", "/courses/{courseId}/leaders"),
                  new RbacCatalog.Route("PUT", "/classes/{classId}/teachers"))),
          Map.entry("TV", List.of(new RbacCatalog.Route("GET", "/teachers"))),
          Map.entry(
              "TEC",
              List.of(
                  new RbacCatalog.Route("POST", "/teachers"),
                  new RbacCatalog.Route("GET", "/auth/users/{userId}"))),
          Map.entry("TEE", List.of(new RbacCatalog.Route("PATCH", "/teachers/{teacherId}"))),
          Map.entry("TED", List.of(new RbacCatalog.Route("DELETE", "/teachers/{teacherId}"))),
          Map.entry("AV", List.of(new RbacCatalog.Route("GET", "/audit-logs"))),
          Map.entry("UL", List.of(new RbacCatalog.Route("GET", "/auth/users/{userId}"))));

  /** 固定协议不建立可变实例。 */
  private CourseContract() {}
}
