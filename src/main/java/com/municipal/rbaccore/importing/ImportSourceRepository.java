package com.municipal.rbaccore.importing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 导入来源通过独立仓储保存，不读取或修改旧业务库。 */
public interface ImportSourceRepository extends JpaRepository<ImportSource, Long> {

  /** 离线全量预检一次加载目标引用，避免逐条来源触发万人查询。 */
  @Override
  @EntityGraph(attributePaths = {"user", "department"})
  List<ImportSource> findAll();

  /** 按来源类型读取少量初始化账号，避免反复加载万人来源及其目标。 */
  @EntityGraph(attributePaths = {"user", "department"})
  List<ImportSource> findAllByKind(ImportSource.Kind kind);

  /** 通过受控导入记录取得已核实共同根或独立初始化账号，不按角色名猜身份。 */
  Optional<ImportSource> findByKindAndSourceKey(ImportSource.Kind kind, String sourceKey);
}
