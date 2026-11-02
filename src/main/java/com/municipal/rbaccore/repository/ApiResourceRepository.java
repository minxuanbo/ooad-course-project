package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.ApiResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiResourceRepository extends JpaRepository<ApiResource, Long> {}
