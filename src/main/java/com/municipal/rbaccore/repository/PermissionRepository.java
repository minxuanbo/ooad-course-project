package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {}
