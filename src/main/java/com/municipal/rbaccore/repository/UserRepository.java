package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 用户持久化；两个登录字段使用相同的二进制字符比较规则。 */
public interface UserRepository extends JpaRepository<User, Long> {

  /** 一次查询保留所有匹配用户，调用方必须拒绝跨用户歧义，不取第一条。 */
  List<User> findByUsernameOrPhone(String username, String phone);

  /** JPA结构校验不覆盖排序规则，读取实际元数据防止导入判重与登录查询不一致。 */
  @Query(value = "SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE()"
      + " AND table_name='users' AND column_name IN ('username','phone')"
      + " AND collation_name='utf8mb4_0900_bin'", nativeQuery = true)
  long countBinaryLoginColumns();
}
