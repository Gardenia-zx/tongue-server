package com.tongue.server.auth.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "doctor_profile")
public class DoctorProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    public Long userId;

    @Column(length = 64)
    public String realName;

    @Column(length = 64)
    public String title;

    @Column(columnDefinition = "text")
    public String introduction;

    @Column(columnDefinition = "text")
    public String specialty;

    @Column(nullable = false, length = 32)
    public String reviewStatus = "PENDING";
}
