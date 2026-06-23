package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.TongueAnalysisTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TongueAnalysisTaskRepository extends JpaRepository<TongueAnalysisTaskEntity, Long> {
    List<TongueAnalysisTaskEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
