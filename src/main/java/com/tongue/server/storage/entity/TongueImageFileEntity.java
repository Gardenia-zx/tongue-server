package com.tongue.server.storage.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tongue_image_file")
public class TongueImageFileEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long fileObjectId;

    public Long reportId;

    @Column(length = 64)
    public String purpose = "tongue_image";
}
