package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.TongueReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TongueReportRepository extends JpaRepository<TongueReportEntity, Long> {
    List<TongueReportEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<TongueReportEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
