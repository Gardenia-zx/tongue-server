package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "tongue_analysis_task")
public class TongueAnalysisTaskEntity extends BaseEntity {

    @Column(nullable = false)
    public Long reportId;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false, length = 32)
    public String status = "PENDING";

    @Column(length = 64)
    public String currentStage = "PENDING";

    public Double progress = 0.0;

    @Column(length = 128)
    public String requestId;

    @Column(length = 128)
    public String traceId;

    @Column(length = 64)
    public String errorCode;

    @Column(columnDefinition = "text")
    public String errorMessage;

    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
}
