package com.tongue.server.review.entity;

import com.tongue.server.persistence.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "doctor_review_comment")
public class DoctorReviewCommentEntity extends BaseEntity {

    @Column(nullable = false)
    public Long reviewOrderId;

    @Column(nullable = false)
    public Long doctorUserId;

    @Column(columnDefinition = "text")
    public String commentText;

    @Column(columnDefinition = "json")
    public String revisedReportJson;
}
