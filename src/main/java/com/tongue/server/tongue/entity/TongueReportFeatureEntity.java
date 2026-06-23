package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tongue_report_feature")
public class TongueReportFeatureEntity extends BaseEntity {

    @Column(nullable = false)
    public Long reportId;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false, length = 128)
    public String featureCode;

    @Column(length = 64)
    public String featureGroup;

    public Double confidence;
}
