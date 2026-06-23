package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.TongueReportEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TongueReportEvidenceRepository extends JpaRepository<TongueReportEvidenceEntity, Long> {
    List<TongueReportEvidenceEntity> findByReportId(Long reportId);
}
