package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_token")
public class UserTokenEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false, length = 128)
    public String tokenHash;

    @Column(nullable = false)
    public LocalDateTime expiresAt;

    public LocalDateTime revokedAt;
}
