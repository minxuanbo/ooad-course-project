package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {}
