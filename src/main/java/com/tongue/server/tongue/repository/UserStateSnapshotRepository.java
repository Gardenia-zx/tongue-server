package com.tongue.server.tongue.repository;

import com.tongue.server.tongue.entity.UserStateSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserStateSnapshotRepository extends JpaRepository<UserStateSnapshotEntity, Long> {
    Optional<UserStateSnapshotEntity> findByReportId(Long reportId);

    Optional<UserStateSnapshotEntity> findByTaskId(Long taskId);

    Optional<UserStateSnapshotEntity> findByReportIdAndUserId(Long reportId, Long userId);
}
