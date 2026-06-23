package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tongue_report_evidence")
public class TongueReportEvidenceEntity extends BaseEntity {

    @Column(nullable = false)
    public Long reportId;

    @Column(length = 128)
    public String chunkId;

    @Column(length = 128)
    public String docId;

    @Column(length = 255)
    public String title;

    @Column(columnDefinition = "text")
    public String content;

    @Column(length = 512)
    public String sourceUri;

    public Double finalScore;
}
