package com.tongue.server.storage.repository;

import com.tongue.server.storage.entity.TongueImageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TongueImageFileRepository extends JpaRepository<TongueImageFileEntity, Long> {
    Optional<TongueImageFileEntity> findFirstByReportIdOrderByCreatedAtDesc(Long reportId);
}
