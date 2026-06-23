package com.tongue.server.review.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_review_order")
public class DoctorReviewOrderEntity extends BaseEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long reportId;

    public Long doctorUserId;

    @Column(nullable = false, length = 32)
    public String status = "SUBMITTED";

    @Column(length = 32)
    public String payStatus = "UNPAID";

    public BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    public String userRemark;

    public LocalDateTime acceptedAt;
    public LocalDateTime completedAt;
    public LocalDateTime canceledAt;
}
