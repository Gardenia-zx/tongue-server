package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.TongueReportFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TongueReportFeatureRepository extends JpaRepository<TongueReportFeatureEntity, Long> {
    List<TongueReportFeatureEntity> findByReportId(Long reportId);

    List<TongueReportFeatureEntity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime createdAt
    );
}
