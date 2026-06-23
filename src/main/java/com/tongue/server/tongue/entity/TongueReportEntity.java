package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "tongue_report")
public class TongueReportEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    public Long taskId;
    public Long imageFileId;

    @Column(length = 128)
    public String threadId;

    @Column(nullable = false, length = 32)
    public String reportStatus = "DRAFT";

    @Column(length = 32)
    public String sourceType = "AI";

    @Column(columnDefinition = "text")
    public String summary;

    @Column(columnDefinition = "text")
    public String featureSummary;

    @Column(columnDefinition = "json")
    public String detectedFeatureCodes;

    @Column(columnDefinition = "json")
    public String standardFeaturesJson;

    @Column(columnDefinition = "text")
    public String ragQuery;

    public Boolean ragGrounded = false;

    @Column(columnDefinition = "json")
    public String ragEvidenceJson;

    @Column(columnDefinition = "json")
    public String draftReportJson;

    @Column(columnDefinition = "text")
    public String riskDisclaimer;

    public LocalDateTime deletedAt;
}
