package com.tongue.server.storage.repository;

import com.tongue.server.storage.entity.FileObjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileObjectRepository extends JpaRepository<FileObjectEntity, Long> {
    List<FileObjectEntity> findByOwnerUserIdAndStatusOrderByCreatedAtDesc(Long ownerUserId, String status);
}
