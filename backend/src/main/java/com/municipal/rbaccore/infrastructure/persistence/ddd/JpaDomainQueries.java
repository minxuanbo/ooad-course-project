package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 共用数据库分页及字面搜索工具，不参与业务筛选规则。 */
final class JpaDomainQueries {
  private JpaDomainQueries() {}

  /** 文本搜索按字面匹配，输入通配符不扩张结果。 */
  static String pattern(String q) {
    String search = q == null ? "" : q.toLowerCase(Locale.ROOT);
    return "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  /** 计数先于offset转换，超大合法页码返回空页和真实总数。 */
  static <E, D> DomainPage<D> read(
      JpaSpecificationExecutor<E> source,
      Specification<E> spec,
      int page,
      int size,
      Function<List<E>, List<D>> mapping) {
    long count = source.count(spec);
    if ((long) page * size >= count) return new DomainPage<>(page, size, count, List.of());
    List<E> rows = source.findAll(spec, PageRequest.of(page, size, Sort.by("id"))).getContent();
    return new DomainPage<>(page, size, count, mapping.apply(rows));
  }
}
