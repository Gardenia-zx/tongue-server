package com.tongue.server.notification.repository;

import com.tongue.server.notification.entity.UserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {
    List<UserNotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
