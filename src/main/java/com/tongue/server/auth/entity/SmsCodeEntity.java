package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "sms_code")
public class SmsCodeEntity extends BaseEntity {

    @Column(nullable = false, length = 32)
    public String phone;

    @Column(nullable = false, length = 16)
    public String code;

    @Column(nullable = false, length = 32)
    public String scene = "LOGIN";

    @Column(nullable = false)
    public LocalDateTime expiresAt;

    public LocalDateTime usedAt;
}
