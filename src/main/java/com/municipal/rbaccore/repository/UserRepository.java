package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
