package com.tongue.server.health.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_health_plan")
public class UserHealthPlanEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long sourceReportId;

    @Column(nullable = false, length = 16)
    public String status = "DRAFT";

    @Column(nullable = false)
    public LocalDate startDate;

    @Column(nullable = false)
    public LocalDate endDate;

    @Column(columnDefinition = "json")
    public String dietGoalJson;

    @Column(columnDefinition = "json")
    public String sleepGoalJson;

    @Column(columnDefinition = "json")
    public String exerciseGoalJson;

    @Column(columnDefinition = "json")
    public String observationItemsJson;

    @Column(columnDefinition = "json")
    public String planContentJson;

    @Column(length = 16)
    public String schemaVersion = "2.0";

    @Column(length = 24)
    public String generationMode = "AI_DRAFT";

    public LocalDateTime activatedAt;

    public LocalDateTime closedAt;
}
