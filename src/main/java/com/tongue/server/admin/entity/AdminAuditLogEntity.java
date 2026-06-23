package com.tongue.server.admin.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLogEntity extends BaseEntity {

    public Long adminUserId;

    @Column(length = 64)
    public String action;

    @Column(length = 64)
    public String targetType;

    public Long targetId;

    @Column(columnDefinition = "text")
    public String detailJson;
}
