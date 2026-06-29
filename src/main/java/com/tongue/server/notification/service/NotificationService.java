package com.tongue.server.notification.service;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.notification.entity.UserNotificationEntity;
import com.tongue.server.notification.repository.UserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final UserNotificationRepository notificationRepository;

    public NotificationService(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void create(
            Long userId,
            String type,
            String title,
            String content,
            String payloadJson
    ) {
        create(userId, type, title, content, payloadJson, null);
    }

    @Transactional
    public void create(
            Long userId,
            String type,
            String title,
            String content,
            String payloadJson,
            LocalDateTime scheduledAt
    ) {
        UserNotificationEntity entity = new UserNotificationEntity();
        entity.userId = userId;
        entity.type = type;
        entity.title = title;
        entity.content = content;
        entity.payloadJson = payloadJson;
        entity.scheduledAt = scheduledAt;
        notificationRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<UserNotificationEntity> myNotifications() {
        return notificationRepository.findVisibleByUserIdOrderByCreatedAtDesc(AuthContext.requireUserId(), LocalDateTime.now());
    }

    @Transactional
    public void markRead(Long notificationId) {
        Long userId = AuthContext.requireUserId();
        UserNotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "通知不存在",
                        null
                ));
        if (!userId.equals(notification.userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有通知访问权限", null);
        }
        notification.readAt = LocalDateTime.now();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead() {
        Long userId = AuthContext.requireUserId();
        for (UserNotificationEntity notification : notificationRepository.findVisibleByUserIdOrderByCreatedAtDesc(userId, LocalDateTime.now())) {
            if (notification.readAt == null) {
                notification.readAt = LocalDateTime.now();
                notificationRepository.save(notification);
            }
        }
    }

    @Transactional
    public void markUnreadTypeRead(Long userId, String type) {
        LocalDateTime now = LocalDateTime.now();
        for (UserNotificationEntity notification : notificationRepository.findByUserIdAndTypeAndReadAtIsNull(userId, type)) {
            notification.readAt = now;
            notificationRepository.save(notification);
        }
    }
}
