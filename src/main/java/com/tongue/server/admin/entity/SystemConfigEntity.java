package com.tongue.server.admin.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "system_config")
public class SystemConfigEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 128)
    public String configKey;

    @Column(columnDefinition = "text")
    public String configValue;

    @Column(length = 64)
    public String configGroup;
}
