package com.tongue.server.tongue.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "user_state_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_state_snapshot_report", columnNames = "report_id"),
                @UniqueConstraint(name = "uk_state_snapshot_task", columnNames = "task_id")
        }
)
public class UserStateSnapshotEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "report_id", nullable = false)
    public Long reportId;

    @Column(name = "task_id", nullable = false)
    public Long taskId;

    @Column(name = "observation_window", length = 32)
    public String observationWindow = "LAST_3_DAYS";

    @Column(name = "sleep_status", length = 64)
    public String sleepStatus;

    @Column(name = "digestion_status", length = 64)
    public String digestionStatus;

    @Column(name = "bowel_status", length = 64)
    public String bowelStatus;

    @Column(name = "current_states_json", columnDefinition = "json")
    public String currentStatesJson;

    @Column(name = "health_goals_json", columnDefinition = "json")
    public String healthGoalsJson;

    @Column(name = "free_description", columnDefinition = "text")
    public String freeDescription;

    public Boolean skipped = false;
}
