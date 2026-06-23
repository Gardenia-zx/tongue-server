package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.TongueReportVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TongueReportVersionRepository extends JpaRepository<TongueReportVersionEntity, Long> {
    List<TongueReportVersionEntity> findByReportIdOrderByVersionNoDesc(Long reportId);

    long countByReportId(Long reportId);
}
