package com.tongue.server.health.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
        name = "user_daily_checkin",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_checkin_user_date", columnNames = {"user_id", "checkin_date"})
)
public class UserDailyCheckinEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "plan_id", nullable = false)
    public Long planId;

    @Column(name = "checkin_date", nullable = false)
    public LocalDate checkinDate;

    public Boolean dietDone = false;
    public Boolean sleepDone = false;
    public Boolean exerciseDone = false;

    @Column(columnDefinition = "json")
    public String observationJson;

    @Column(columnDefinition = "text")
    public String note;
}
