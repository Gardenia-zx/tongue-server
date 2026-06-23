package com.tongue.server.storage.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_object")
public class FileObjectEntity extends BaseEntity {

    @Column(nullable = false)
    public Long ownerUserId;

    @Column(nullable = false, length = 32)
    public String storageMode = "local";

    @Column(length = 128)
    public String bucket;

    @Column(nullable = false, length = 512)
    public String objectKey;

    @Column(length = 512)
    public String storagePath;

    @Column(length = 512)
    public String publicUrl;

    @Column(length = 255)
    public String originalFilename;

    @Column(length = 128)
    public String contentType;

    public Long fileSize;

    @Column(length = 128)
    public String checksum;

    @Column(length = 64)
    public String purpose;

    @Column(nullable = false, length = 32)
    public String status = "ACTIVE";

    public LocalDateTime deletedAt;
}
