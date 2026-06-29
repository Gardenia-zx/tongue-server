package com.tongue.server.notification.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification")
public class UserNotificationEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false, length = 64)
    public String type;

    @Column(length = 128)
    public String title;

    @Column(columnDefinition = "text")
    public String content;

    @Column(columnDefinition = "json")
    public String payloadJson;

    public LocalDateTime scheduledAt;

    public LocalDateTime readAt;
}
