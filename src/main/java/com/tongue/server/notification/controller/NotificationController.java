package com.tongue.server.notification.controller;

import com.tongue.server.common.ApiResponse;
import com.tongue.server.notification.entity.UserNotificationEntity;
import com.tongue.server.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<UserNotificationEntity>> list() {
        return ApiResponse.success(notificationService.myNotifications());
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<Object> read(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId);
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Object> readAll() {
        notificationService.markAllRead();
        return ApiResponse.success(null);
    }
}
