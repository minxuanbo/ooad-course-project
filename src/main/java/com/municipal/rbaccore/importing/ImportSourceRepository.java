package com.municipal.rbaccore.importing;

import org.springframework.data.jpa.repository.JpaRepository;

/** 导入来源通过独立仓储保存，不读取或修改旧业务库。 */
public interface ImportSourceRepository extends JpaRepository<ImportSource, Long> {
}
