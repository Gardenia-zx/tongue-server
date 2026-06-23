package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tongue_report_version")
public class TongueReportVersionEntity extends BaseEntity {

    @Column(nullable = false)
    public Long reportId;

    @Column(nullable = false)
    public Integer versionNo;

    @Column(length = 32)
    public String sourceType = "AI";

    @Column(columnDefinition = "text")
    public String summary;

    @Column(columnDefinition = "json")
    public String reportJson;

    public Long doctorReviewId;
}
