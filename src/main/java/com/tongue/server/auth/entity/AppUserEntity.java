package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUserEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    public String phone;

    @Column(nullable = false, length = 32)
    public String role = "USER";

    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";
}
