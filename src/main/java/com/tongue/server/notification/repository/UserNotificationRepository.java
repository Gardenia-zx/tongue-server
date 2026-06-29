package com.tongue.server.notification.repository;

import com.tongue.server.notification.entity.UserNotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {
    List<UserNotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    List<UserNotificationEntity> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserNotificationEntity> findByUserIdAndTypeAndReadAtIsNull(Long userId, String type);

    @Query("select n from UserNotificationEntity n where n.userId = :userId and (n.scheduledAt is null or n.scheduledAt <= :now) order by n.createdAt desc")
    List<UserNotificationEntity> findVisibleByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Query("select count(n) from UserNotificationEntity n where n.userId = :userId and n.readAt is null and (n.scheduledAt is null or n.scheduledAt <= :now)")
    long countVisibleUnreadByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Query("select n from UserNotificationEntity n where n.userId = :userId and (n.scheduledAt is null or n.scheduledAt <= :now) order by n.createdAt desc")
    List<UserNotificationEntity> findTopVisibleByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
