package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    public Long userId;

    @Column(length = 64)
    public String nickname;

    @Column(length = 16)
    public String gender;

    public Integer age;
    public Long avatarFileId;

    @Column(columnDefinition = "text")
    public String healthFocus;
}
