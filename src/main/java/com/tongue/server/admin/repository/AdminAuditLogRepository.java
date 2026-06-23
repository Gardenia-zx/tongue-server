package com.tongue.server.admin.repository;

import com.tongue.server.admin.entity.AdminAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, Long> {
}
