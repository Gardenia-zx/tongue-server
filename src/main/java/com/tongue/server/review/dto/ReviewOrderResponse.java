package com.tongue.server.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ReviewOrderResponse {
    @JsonProperty("review_id")
    public Long reviewId;
    @JsonProperty("report_id")
    public Long reportId;
    @JsonProperty("user_id")
    public Long userId;
    @JsonProperty("doctor_user_id")
    public Long doctorUserId;
    public String status;
    @JsonProperty("pay_status")
    public String payStatus;
    @JsonProperty("price_amount")
    public Object priceAmount;
    @JsonProperty("user_remark")
    public String userRemark;
    @JsonProperty("created_at")
    public LocalDateTime createdAt;
}
