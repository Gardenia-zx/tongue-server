package com.tongue.server.admin.repository;

import com.tongue.server.admin.entity.AdminUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {
    Optional<AdminUserEntity> findByUsername(String username);
}
