package com.tongue.server.admin.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "admin_user")
public class AdminUserEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    public String username;

    @Column(nullable = false, length = 128)
    public String passwordHash;

    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";
}
