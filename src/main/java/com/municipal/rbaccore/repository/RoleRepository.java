package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {}
