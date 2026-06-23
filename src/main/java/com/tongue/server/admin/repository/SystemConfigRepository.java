package com.tongue.server.admin.repository;

import com.tongue.server.admin.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {
    Optional<SystemConfigEntity> findByConfigKey(String configKey);
}
